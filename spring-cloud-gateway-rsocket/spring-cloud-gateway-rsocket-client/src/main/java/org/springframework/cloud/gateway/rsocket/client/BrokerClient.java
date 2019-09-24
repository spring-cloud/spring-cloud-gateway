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

package org.springframework.cloud.gateway.rsocket.client;

import java.util.function.Consumer;

import reactor.core.publisher.Mono;

import org.springframework.cloud.gateway.rsocket.common.autoconfigure.Broker;
import org.springframework.cloud.gateway.rsocket.common.metadata.Forwarding;
import org.springframework.messaging.rsocket.RSocketRequester;

public class BrokerClient {

	private final ClientProperties properties;

	private final RSocketRequester.Builder builder;

	public BrokerClient(ClientProperties properties, RSocketRequester.Builder builder) {
		this.properties = properties;
		this.builder = builder;
	}

	public ClientProperties getProperties() {
		return this.properties;
	}

	public RSocketRequester.Builder getRSocketRequesterBuilder() {
		return this.builder;
	}

	public Mono<RSocketRequester> connect() {
		return connect(builder);
	}

	public Mono<RSocketRequester> connect(RSocketRequester.Builder requesterBuilder) {
		Broker broker = properties.getBroker();
		switch (broker.getConnectionType()) {
		case WEBSOCKET:
			return requesterBuilder.connectWebSocket(broker.getWsUri());
		}
		return requesterBuilder.connectTcp(broker.getHost(), broker.getPort());
	}

	public Consumer<RSocketRequester.MetadataSpec<?>> forwarding(String destServiceName) {
		return spec -> {
			Forwarding forwarding = Forwarding.of(properties.getRouteId())
					.serviceName(destServiceName).build();
			spec.metadata(forwarding, Forwarding.FORWARDING_MIME_TYPE);
		};
	}

	public Consumer<RSocketRequester.MetadataSpec<?>> forwarding(
			Consumer<Forwarding.Builder> builderConsumer) {
		return spec -> {
			Forwarding.Builder builder = Forwarding.of(properties.getRouteId());
			builderConsumer.accept(builder);
			spec.metadata(builder.build(), Forwarding.FORWARDING_MIME_TYPE);
		};
	}

}
