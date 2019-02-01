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

package org.springframework.cloud.gateway.rsocket.server;

import java.util.List;
import java.util.Map;

import io.rsocket.AbstractRSocket;
import io.rsocket.Payload;
import io.rsocket.RSocket;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.springframework.cloud.gateway.rsocket.registry.Registry;
import org.springframework.cloud.gateway.rsocket.support.Metadata;
import org.springframework.util.CollectionUtils;

/**
 * Acts as a proxy to other registered sockets. Looks up target RSocket
 * via Registry. Creates GatewayExchange and executes a GatewayFilterChain.
 */
public class GatewayRSocket extends AbstractRSocket {

	private static final Log log = LogFactory.getLog(GatewayRSocket.class);

	private final Registry registry;
	private final GatewayFilterChain filterChain;

	public GatewayRSocket(Registry registry, List<GatewayFilter> filters) {
		this.registry = registry;
		this.filterChain = new GatewayFilterChain(filters);
	}

	@Override
	public Mono<Void> fireAndForget(Payload payload) {
		return findRSocket(payload)
				.flatMap(rSocket -> rSocket.fireAndForget(payload));
	}

	@Override
	public Flux<Payload> requestChannel(Publisher<Payload> payloads) {
		return Flux.from(payloads)
				.switchOnFirst((signal, payloadFlux) -> {
					if (!signal.hasValue()) {
						return payloadFlux;
					}

					return findRSocket(signal.get())
							.flatMapMany(rSocket -> rSocket.requestChannel(payloadFlux));
				});
	}

	@Override
	public Mono<Payload> requestResponse(Payload payload) {
		return findRSocket(payload)
				.flatMap(rSocket -> rSocket.requestResponse(payload));
	}

	@Override
	public Flux<Payload> requestStream(Payload payload) {
		return findRSocket(payload)
				.flatMapMany(rSocket -> rSocket.requestStream(payload));
	}

	private Mono<RSocket> findRSocket(Payload payload) {
		Map<String, String> metadata = getRoutingMetadata(payload);
		RSocket service = findRSocket(metadata);

		if (service == null) {
			PendingRequestRSocket pending = new PendingRequestRSocket(metadata);
			this.registry.addListener(pending); //TODO: deal with removing?
			service = pending;
		}

		GatewayExchange exchange = new GatewayExchange(payload);

		RSocket rSocket = service;

		return this.filterChain.filter(exchange)
				.log("gateway filter chain")
				.map(bool -> rSocket);
	}

	private RSocket findRSocket(Map<String, String> properties) {
		if (properties == null) return null; //TODO: error

		RSocket rsocket = registry.getRegistered(properties);

		if (rsocket == null) {
			log.debug("Unable to find destination RSocket for " + properties);
		}
		// TODO: deal with connecting to cluster?

		return rsocket;
	}

	private Map<String, String> getRoutingMetadata(Payload payload) {
		if (payload == null || !payload.hasMetadata()) { // and metadata is routing
			return null;
		}

		// TODO: deal with composite metadata

		Map<String, String> properties = Metadata.decodeProperties(payload.sliceMetadata());

		if (CollectionUtils.isEmpty(properties)) {
			return null;
		}

		log.debug("found routing metadata " + properties);
		return properties;
	}


}

