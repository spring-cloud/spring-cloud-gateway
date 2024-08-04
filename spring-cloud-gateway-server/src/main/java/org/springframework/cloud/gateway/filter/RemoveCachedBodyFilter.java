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

package org.springframework.cloud.gateway.filter;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import reactor.core.publisher.Mono;

import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.PooledDataBuffer;
import org.springframework.web.server.ServerWebExchange;

import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.CACHED_REQUEST_BODY_ATTR;

public class RemoveCachedBodyFilter implements GlobalFilter, Ordered {

	private static final Log log = LogFactory.getLog(RemoveCachedBodyFilter.class);

	@Override
	public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
		return chain.filter(exchange).doFinally(s -> {
			Object attribute = exchange.getAttributes().remove(CACHED_REQUEST_BODY_ATTR);
			if (attribute != null && attribute instanceof PooledDataBuffer) {
				PooledDataBuffer dataBuffer = (PooledDataBuffer) attribute;
				if (dataBuffer.isAllocated()) {
					if (log.isTraceEnabled()) {
						log.trace("releasing cached body in exchange attribute");
					}
					// ensure proper release
					while (!dataBuffer.release()) {
						// release() counts down until zero, will never be infinite loop
					}
				}
			}
		});
	}

	@Override
	public int getOrder() {
		return HIGHEST_PRECEDENCE;
	}

}
