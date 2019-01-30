/*
 * Copyright 2018-2019 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package org.springframework.cloud.gateway.rsocket;

import io.rsocket.ConnectionSetupPayload;
import org.springframework.core.Ordered;
import reactor.core.publisher.Mono;

import org.springframework.cloud.gateway.rsocket.GatewaySocketAcceptor.SocketAcceptorExchange;
import org.springframework.cloud.gateway.rsocket.GatewaySocketAcceptor.SocketAcceptorFilter;
import org.springframework.cloud.gateway.rsocket.GatewaySocketAcceptor.SocketAcceptorFilterChain;

import java.util.Collections;
import java.util.List;

public class RegistrySocketAcceptorFilter implements SocketAcceptorFilter, Ordered {
	private final Registry registry;

	public RegistrySocketAcceptorFilter(Registry registry) {
		this.registry = registry;
	}

	@Override
	public Mono<Boolean> filter(SocketAcceptorExchange exchange, SocketAcceptorFilterChain chain) {
		ConnectionSetupPayload setup = exchange.getSetup();
		if (setup.hasMetadata()) { // and setup.metadataMimeType() is Announcement metadata
			String annoucementMetadata = Metadata.decodeAnnouncement(setup.sliceMetadata());
			List<String> tags = Collections.singletonList(annoucementMetadata);
			registry.register(tags, exchange.getSendingSocket());
		}

		return chain.filter(exchange);
	}

	@Override
	public int getOrder() {
		return HIGHEST_PRECEDENCE + 1000;
	}
}
