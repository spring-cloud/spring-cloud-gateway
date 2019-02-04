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

import java.util.logging.Level;

import io.rsocket.AbstractRSocket;
import io.rsocket.Payload;
import io.rsocket.RSocket;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.reactivestreams.Publisher;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.springframework.cloud.gateway.rsocket.registry.Registry;
import org.springframework.cloud.gateway.rsocket.route.Routes;

import static org.springframework.cloud.gateway.rsocket.server.GatewayExchange.ROUTE_ATTR;
import static org.springframework.cloud.gateway.rsocket.server.GatewayExchange.Type.FIRE_AND_FORGET;
import static org.springframework.cloud.gateway.rsocket.server.GatewayExchange.Type.REQUEST_CHANNEL;
import static org.springframework.cloud.gateway.rsocket.server.GatewayExchange.Type.REQUEST_RESPONSE;
import static org.springframework.cloud.gateway.rsocket.server.GatewayExchange.Type.REQUEST_STREAM;
import static org.springframework.cloud.gateway.rsocket.server.GatewayFilterChain.executeFilterChain;

/**
 * Acts as a proxy to other registered sockets. Creates a GatewayExchange and attempts
 * to locate a Route. If a Route is found, it is added to the exchange and the filter
 * chains is executed againts the Route's filters. If the filter chain is successful,
 * an attempt to locate a target RSocket via the Registry is executed. If not found
 * a pending RSocket * is returned.
 */
public class GatewayRSocket extends AbstractRSocket {

	private static final Log log = LogFactory.getLog(GatewayRSocket.class);

	private final Registry registry;
	private final Routes routes;

	public GatewayRSocket(Registry registry, Routes routes) {
		this.registry = registry;
		this.routes = routes;
	}

	protected Registry getRegistry() {
		return registry;
	}

	protected Routes getRoutes() {
		return routes;
	}

	@Override
	public Mono<Void> fireAndForget(Payload payload) {
		return findRSocket(FIRE_AND_FORGET, payload)
				.flatMap(rSocket -> rSocket.fireAndForget(payload));
	}

	@Override
	public Flux<Payload> requestChannel(Publisher<Payload> payloads) {
		return Flux.from(payloads)
				.switchOnFirst((signal, payloadFlux) -> {
					if (!signal.hasValue()) {
						return payloadFlux;
					}

					return findRSocket(REQUEST_CHANNEL, signal.get())
							.flatMapMany(rSocket -> rSocket.requestChannel(payloadFlux));
				});
	}

	@Override
	public Mono<Payload> requestResponse(Payload payload) {
		return findRSocket(REQUEST_RESPONSE, payload)
				.flatMap(rSocket -> rSocket.requestResponse(payload));
	}

	@Override
	public Flux<Payload> requestStream(Payload payload) {
		return findRSocket(REQUEST_STREAM, payload)
				.flatMapMany(rSocket -> rSocket.requestStream(payload));
	}

	/**
	 * Create GatewayExchange and attempt to locate target RSocket via filter chain.
	 * If not found, create a pending RSocket.
	 * @param type
	 * @param payload
	 * @return
	 */
	private Mono<RSocket> findRSocket(GatewayExchange.Type type, Payload payload) {
		GatewayExchange exchange = GatewayExchange.fromPayload(type, payload);
		return findRSocket(exchange)
				// if a route can't be found or registered RSocket, create pending
				.switchIfEmpty(createPendingRSocket(exchange));
	}

	private Mono<RSocket> createPendingRSocket(GatewayExchange exchange) {
		if (log.isDebugEnabled()) {
			log.debug("creating pending RSocket for " + exchange.getRoutingMetadata());
		}
		PendingRequestRSocket pending = constructPendingRSocket(exchange);
		Disposable disposable = this.registry.addListener(pending);
		pending.setSubscriptionDisposable(disposable);
		return Mono.just(pending);
	}

	/* for testing */ PendingRequestRSocket constructPendingRSocket(GatewayExchange exchange) {
		return new PendingRequestRSocket(routes, exchange);
	}

	/**
	 * First locate Route. If found, put route in exchange and execute filter chain.
	 * If successful, locate target RSocket.
	 * @param exchange
	 * @return Target RSocket or empty.
	 */
	private Mono<RSocket> findRSocket(GatewayExchange exchange) {
		return this.routes.findRoute(exchange)
				.log("find route", Level.FINE)
				.flatMap(route -> {
					// put route in exchange for later use
					exchange.getAttributes().put(ROUTE_ATTR, route);
					return executeFilterChain(route.getFilters(), exchange)
							.map(success -> {
								RSocket rsocket = registry.getRegistered(exchange.getRoutingMetadata());

								if (rsocket == null && log.isDebugEnabled()) {
									log.debug("Unable to find destination RSocket for " + exchange.getRoutingMetadata());
								}
								return rsocket;
							});
				});

		// TODO: deal with connecting to cluster?
	}

}
