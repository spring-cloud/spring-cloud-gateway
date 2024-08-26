/*
 * Copyright 2013-2024 the original author or authors.
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

package org.springframework.cloud.gateway.handler;

import reactor.core.publisher.Mono;

import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.core.Ordered;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebExceptionHandler;

/**
 * @author Nan Chiu
 */
public class RemoveCachedBodyWebExceptionHandler implements WebExceptionHandler, Ordered {

	@Override
	public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
		ServerWebExchangeUtils.clearCachedRequestBody(exchange);
		return Mono.error(ex);
	}

	@Override
	public int getOrder() {
		return HIGHEST_PRECEDENCE;
	}

}
