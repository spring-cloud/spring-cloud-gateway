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

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import io.rsocket.AbstractRSocket;
import io.rsocket.Payload;
import io.rsocket.RSocket;
import io.rsocket.ResponderRSocket;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.reactivestreams.Publisher;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.springframework.cloud.gateway.rsocket.autoconfigure.GatewayRSocketProperties;
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
public class GatewayRSocket extends AbstractRSocket implements ResponderRSocket {

	private static final Log log = LogFactory.getLog(GatewayRSocket.class);

	private final Registry registry;
	private final Routes routes;
	private final MeterRegistry meterRegistry;
	private final GatewayRSocketProperties properties;
	private final Map<String, String> metadata;

	GatewayRSocket(Registry registry, Routes routes, MeterRegistry meterRegistry,
			GatewayRSocketProperties properties, Map<String, String> metadata) {
		this.registry = registry;
		this.routes = routes;
		this.meterRegistry = meterRegistry;
		this.properties = properties;
		this.metadata = metadata;
	}


	protected Registry getRegistry() {
		return registry;
	}

	protected Routes getRoutes() {
		return routes;
	}

	@Override
	public Mono<Void> fireAndForget(Payload payload) {
		GatewayExchange exchange = GatewayExchange.fromPayload(FIRE_AND_FORGET, payload);
		Tags tags = getTags(exchange);
		return findRSocketOrCreatePending(exchange)
				.flatMap(rSocket -> rSocket.fireAndForget(payload))
				.doOnError(t -> count("forward.request.fnf.error", tags))
				.doFinally(s -> count("forward.request.fnf", tags));
	}

	private Tags getTags(GatewayExchange exchange) {
		//TODO: add tags to exchange
		String responderName = this.metadata.get("name");
		String responderId = this.metadata.get("id");
		String requestorName = exchange.getRoutingMetadata().get("name");
		//String requestorId = TODO: how to get requestorId (it's part of metadata in Registry)
		//TODO: deal with missing tags?
		return Tags.of("requester.name", requestorName, //"requester.id", requestorId,
				"responder.name", responderName, "responder.id", responderId,
				"gateway.id", this.properties.getId());
	}

	@Override
	public Flux<Payload> requestChannel(Payload payload, Publisher<Payload> payloads) {
		GatewayExchange exchange = GatewayExchange.fromPayload(REQUEST_CHANNEL, payload);
		Tags tags = getTags(exchange);
		Tags responderTags = tags.and("source", "responder");
		return findRSocketOrCreatePending(exchange)
				.flatMapMany(rSocket -> {
					Tags requesterTags = tags.and("source", "requester");
					Flux<Payload> flux = Flux.from(payloads)
							.doOnNext(s -> count("forward.request.channel.payload", requesterTags))
							.doOnError(t -> count("forward.request.channel.error", requesterTags))
							.doFinally(s -> count("forward.request.channel", requesterTags));

					if (rSocket instanceof ResponderRSocket) {
						ResponderRSocket socket = (ResponderRSocket) rSocket;
						return socket.requestChannel(payload, flux)
								.log(GatewayRSocket.class.getName()+".request-channel", Level.FINE);
					}
					return rSocket.requestChannel(flux);
				})
				.doOnNext(s -> count("forward.request.channel.payload", responderTags))
				.doOnError(t -> count("forward.request.channel.error", responderTags))
				.doFinally(s -> count("forward.request.channel", responderTags));
	}

	private void count(String name, Tags responderTags) {
		this.meterRegistry.counter(name, responderTags).increment();
	}

	@Override
	public Mono<Payload> requestResponse(Payload payload) {
		AtomicReference<Timer.Sample> timer = new AtomicReference<>();
		GatewayExchange exchange = GatewayExchange.fromPayload(REQUEST_RESPONSE, payload);
		Tags tags = getTags(exchange);
		return findRSocketOrCreatePending(exchange)
				.flatMap(rSocket -> rSocket.requestResponse(payload))
				.doOnSubscribe(s -> timer.set(Timer.start(meterRegistry)))
				.doOnError(t -> count("forward.request.response.error", tags))
				.doFinally(s -> timer.get().stop(meterRegistry.timer("forward.request.response", tags)));
	}

	@Override
	public Flux<Payload> requestStream(Payload payload) {
		GatewayExchange exchange = GatewayExchange.fromPayload(REQUEST_STREAM, payload);
		Tags tags = getTags(exchange);
		return findRSocketOrCreatePending(exchange)
				.flatMapMany(rSocket -> rSocket.requestStream(payload))
				// S N E F
				//TODO: move tagnames to enum
				.doOnNext(s -> count("forward.request.stream.payload", tags))
				.doOnError(t -> count("forward.request.stream.error", tags))
				.doFinally(s -> count("forward.request.stream", tags));
	}

	/**
	 * Attempt to locate target RSocket via filter chain.
	 * If not found, create a pending RSocket.
	 * @param exchange
	 * @return
	 */
	private Mono<RSocket> findRSocketOrCreatePending(GatewayExchange exchange) {
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
				.log(GatewayRSocket.class.getName()+".find route", Level.FINE)
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

	public static class Factory {
		private final Registry registry;
		private final Routes routes;
		private final MeterRegistry meterRegistry;
		private final GatewayRSocketProperties properties;

		public Factory(Registry registry, Routes routes, MeterRegistry meterRegistry,
				GatewayRSocketProperties properties) {
			this.registry = registry;
			this.routes = routes;
			this.meterRegistry = meterRegistry;
			this.properties = properties;
		}

		public GatewayRSocket create(Map<String, String> metadata) {
			return new GatewayRSocket(this.registry, this.routes, this.meterRegistry,
					this.properties, metadata);
		}
	}

}
