/*
 * Copyright 2013-2019 the original author or authors.
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

package org.springframework.cloud.gateway.filter;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.core.io.buffer.PooledDataBuffer;
import org.springframework.http.server.reactive.ServerHttpRequestDecorator;
import org.springframework.web.server.ServerWebExchange;

public class AlwaysRetainBodyGlobalFilter implements GlobalFilter, Ordered {

	private static final Log log = LogFactory.getLog(AlwaysRetainBodyGlobalFilter.class);

	/**
	 * cache key.
	 */
	public static final String ALWAYS_CACHE_REQUEST_BODY_KEY = "alwaysCacheRequestBody";

	@Override
	public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
		Object body = exchange.getAttributeOrDefault(ALWAYS_CACHE_REQUEST_BODY_KEY, null);

		if (body != null) {
			return chain.filter(exchange);
		}

		return DataBufferUtils.join(exchange.getRequest().getBody())
				.flatMap(dataBuffer -> {
					if (dataBuffer.readableByteCount() > 0) {
						if (log.isTraceEnabled()) {
							log.trace("retaining body in exchange attribute");
						}
						exchange.getAttributes().put(ALWAYS_CACHE_REQUEST_BODY_KEY,
								dataBuffer);
					}

					ServerHttpRequestDecorator decorator = new ServerHttpRequestDecorator(
							exchange.getRequest()) {
						@Override
						public Flux<DataBuffer> getBody() {
							return Mono.<DataBuffer>fromSupplier(() -> {
								if (exchange.getAttributeOrDefault(
										ALWAYS_CACHE_REQUEST_BODY_KEY, null) == null) {
									// probably == downstream closed
									return null;
								}
								PooledDataBuffer pdb = (PooledDataBuffer) dataBuffer;
								return pdb.retain();
							}).flux();
						}
					};
					return chain.filter(exchange.mutate().request(decorator).build());
				}).switchIfEmpty(chain.filter(exchange));
	}

	@Override
	public int getOrder() {
		return -10;
	}

}
