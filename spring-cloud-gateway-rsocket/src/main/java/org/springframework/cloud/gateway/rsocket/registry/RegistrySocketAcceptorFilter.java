/*
 * Copyright 2018-2019 the original author or authors.
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

package org.springframework.cloud.gateway.rsocket.registry;

import reactor.core.publisher.Mono;

import org.springframework.cloud.gateway.rsocket.socketacceptor.SocketAcceptorExchange;
import org.springframework.cloud.gateway.rsocket.socketacceptor.SocketAcceptorFilter;
import org.springframework.cloud.gateway.rsocket.socketacceptor.SocketAcceptorFilterChain;
import org.springframework.core.Ordered;
import org.springframework.util.StringUtils;

/**
 * Filter that registers the SendingSocket.
 */
public class RegistrySocketAcceptorFilter implements SocketAcceptorFilter, Ordered {

	private final Registry registry;

	public RegistrySocketAcceptorFilter(Registry registry) {
		this.registry = registry;
	}

	@Override
	public Mono<Success> filter(SocketAcceptorExchange exchange,
			SocketAcceptorFilterChain chain) {
		if (exchange.getMetadata() != null
				&& StringUtils.hasLength(exchange.getMetadata().getName())) {
			this.registry.register(exchange.getMetadata(), exchange.getSendingSocket());
		}

		return chain.filter(exchange);
	}

	@Override
	public int getOrder() {
		return HIGHEST_PRECEDENCE + 1000;
	}

}
