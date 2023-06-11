/*
 * Copyright 2013-2020 the original author or authors.
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

import java.time.Duration;

import reactor.core.publisher.Mono;

import org.springframework.cache.Cache;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.filter.NettyWriteResponseFilter;
import org.springframework.core.Ordered;
import org.springframework.web.server.ServerWebExchange;

import static org.springframework.cloud.gateway.filter.factory.cache.LocalResponseCacheGatewayFilterFactory.LOCAL_RESPONSE_CACHE_FILTER_APPLIED;

/**
 * Caches responses for routes that don't have the
 * {@link LocalResponseCacheGatewayFilterFactory} configured.
 *
 * @author Ignacio Lozano
 * @author Marta Medio
 */
public class GlobalLocalResponseCacheGatewayFilter implements GlobalFilter, Ordered {

	private final ResponseCacheGatewayFilter responseCacheGatewayFilter;

	public GlobalLocalResponseCacheGatewayFilter(ResponseCacheManagerFactory cacheManagerFactory, Cache globalCache,
			Duration configuredTimeToLive) {
		responseCacheGatewayFilter = new ResponseCacheGatewayFilter(
				cacheManagerFactory.create(globalCache, configuredTimeToLive));
	}

	@Override
	public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
		if (exchange.getAttributes().get(LOCAL_RESPONSE_CACHE_FILTER_APPLIED) == null) {
			return responseCacheGatewayFilter.filter(exchange, chain);
		}
		return chain.filter(exchange);
	}

	@Override
	public int getOrder() {
		return NettyWriteResponseFilter.WRITE_RESPONSE_FILTER_ORDER - 2;
	}

}
