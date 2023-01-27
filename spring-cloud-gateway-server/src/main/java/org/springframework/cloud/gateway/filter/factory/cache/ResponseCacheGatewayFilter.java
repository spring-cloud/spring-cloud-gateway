/*
 * Copyright 2013-2022 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.cloud.gateway.filter.factory.cache;

import java.util.Optional;

import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.NettyWriteResponseFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.http.server.reactive.ServerHttpResponseDecorator;
import org.springframework.web.server.ServerWebExchange;

import static org.springframework.cloud.gateway.filter.factory.cache.LocalResponseCacheGatewayFilterFactory.LOCAL_RESPONSE_CACHE_FILTER_APPLIED;

/**
 * {@literal LocalResponseCache} Gateway Filter that stores HTTP Responses in a cache, so
 * latency and upstream overhead is reduced.
 *
 * @author Marta Medio
 * @author Ignacio Lozano
 */
public class ResponseCacheGatewayFilter implements GatewayFilter, Ordered {

	private final ResponseCacheManager responseCacheManager;

	public ResponseCacheGatewayFilter(ResponseCacheManager responseCacheManager) {
		this.responseCacheManager = responseCacheManager;
	}

	@Override
	public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
		if (responseCacheManager.isRequestCacheable(exchange.getRequest())) {
			exchange.getAttributes().put(LOCAL_RESPONSE_CACHE_FILTER_APPLIED, true);
			return filterWithCache(exchange, chain);
		}
		else {
			return chain.filter(exchange);
		}
	}

	@Override
	public int getOrder() {
		return NettyWriteResponseFilter.WRITE_RESPONSE_FILTER_ORDER - 3;
	}

	private Mono<Void> filterWithCache(ServerWebExchange exchange, GatewayFilterChain chain) {
		final String metadataKey = responseCacheManager.resolveMetadataKey(exchange);
		Optional<CachedResponse> cached = responseCacheManager.getFromCache(exchange.getRequest(), metadataKey);

		if (cached.isPresent()) {
			return responseCacheManager.processFromCache(exchange, metadataKey, cached.get());
		}
		else {
			return chain
					.filter(exchange.mutate().response(new CachingResponseDecorator(metadataKey, exchange)).build());
		}
	}

	private class CachingResponseDecorator extends ServerHttpResponseDecorator {

		private final String metadataKey;

		private final ServerWebExchange exchange;

		CachingResponseDecorator(String metadataKey, ServerWebExchange exchange) {
			super(exchange.getResponse());
			this.metadataKey = metadataKey;
			this.exchange = exchange;
		}

		@Override
		public Mono<Void> writeWith(Publisher<? extends DataBuffer> body) {
			final ServerHttpResponse response = exchange.getResponse();

			Flux<DataBuffer> decoratedBody;
			if (responseCacheManager.isResponseCacheable(response)) {
				decoratedBody = responseCacheManager.processFromUpstream(metadataKey, exchange,
						(Flux<DataBuffer>) body);
			}
			else {
				decoratedBody = (Flux<DataBuffer>) body;
			}

			return super.writeWith(decoratedBody);
		}

	}

}
