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

package org.springframework.cloud.gateway.rsocket.server;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
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
import org.springframework.cloud.gateway.rsocket.registry.LoadBalancedRSocket;
import org.springframework.cloud.gateway.rsocket.registry.Registry;
import org.springframework.cloud.gateway.rsocket.route.Route;
import org.springframework.cloud.gateway.rsocket.route.Routes;
import org.springframework.cloud.gateway.rsocket.support.Metadata;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import static org.springframework.cloud.gateway.rsocket.server.GatewayExchange.ROUTE_ATTR;
import static org.springframework.cloud.gateway.rsocket.server.GatewayExchange.Type.FIRE_AND_FORGET;
import static org.springframework.cloud.gateway.rsocket.server.GatewayExchange.Type.REQUEST_CHANNEL;
import static org.springframework.cloud.gateway.rsocket.server.GatewayExchange.Type.REQUEST_RESPONSE;
import static org.springframework.cloud.gateway.rsocket.server.GatewayExchange.Type.REQUEST_STREAM;
import static org.springframework.cloud.gateway.rsocket.server.GatewayFilterChain.executeFilterChain;

/**
 * Acts as a proxy to other registered sockets. Creates a GatewayExchange and attempts to
 * locate a Route. If a Route is found, it is added to the exchange and the filter chains
 * is executed againts the Route's filters. If the filter chain is successful, an attempt
 * to locate a target RSocket via the Registry is executed. If not found a pending RSocket
 * * is returned.
 */
public class GatewayRSocket extends AbstractRSocket implements ResponderRSocket {

	private static final Log log = LogFactory.getLog(GatewayRSocket.class);

	private final Registry registry;

	private final Routes routes;

	private final MeterRegistry meterRegistry;

	private final GatewayRSocketProperties properties;

	private final Metadata metadata;

	GatewayRSocket(Registry registry, Routes routes, MeterRegistry meterRegistry,
			GatewayRSocketProperties properties, Metadata metadata) {
		this.registry = registry;
		this.routes = routes;
		this.meterRegistry = meterRegistry;
		this.properties = properties;
		this.metadata = metadata;
		this.onClose().doOnSuccess(v -> registry.deregister(metadata))
				// .doOnNext(v -> log.error("OnClose doOnNext"))
				.doOnError(t -> {
					if (log.isErrorEnabled()) {
						log.error("Error received, deregistering " + metadata, t);
					}
					registry.deregister(metadata);
				})
				// .doOnTerminate(() -> log.error("OnClose doOnTerminate"))
				// .doFinally(st -> log.error("OnClose doFinally"))
				.subscribe();
	}

	protected Registry getRegistry() {
		return registry;
	}

	protected Routes getRoutes() {
		return routes;
	}

	@Override
	public Mono<Void> fireAndForget(Payload payload) {
		GatewayExchange exchange = createExchange(FIRE_AND_FORGET, payload);
		return findRSocketOrCreatePending(exchange)
				.flatMap(rSocket -> rSocket.fireAndForget(payload))
				.doOnError(t -> count(exchange, "error"))
				.doFinally(s -> count(exchange, ""));
	}

	private GatewayExchange createExchange(GatewayExchange.Type type, Payload payload) {
		GatewayExchange exchange = GatewayExchange.fromPayload(type, payload);
		Tags tags = getTags(exchange);
		exchange.setTags(tags);
		return exchange;
	}

	private Tags getTags(GatewayExchange exchange) {
		// TODO: add tags to exchange
		String requesterName = this.metadata.getName();
		String requesterId = this.metadata.get("id");
		String responderName = exchange.getRoutingMetadata().getName();
		Assert.hasText(responderName, "responderName must not be empty");
		Assert.hasText(requesterId, "requesterId must not be empty");
		Assert.hasText(requesterName, "requesterName must not be empty");
		// responder.id happens in a callback, later
		return Tags.of("requester.name", requesterName, "responder.name", responderName,
				"requester.id", requesterId, "gateway.id", this.properties.getId());
	}

	@Override
	public Flux<Payload> requestChannel(Payload payload, Publisher<Payload> payloads) {
		GatewayExchange exchange = createExchange(REQUEST_CHANNEL, payload);
		Tags responderTags = Tags.of("source", "responder");
		return findRSocketOrCreatePending(exchange).flatMapMany(rSocket -> {
			Tags requesterTags = Tags.of("source", "requester");
			Flux<Payload> flux = Flux.from(payloads)
					.doOnNext(s -> count(exchange, "payload", requesterTags))
					.doOnError(t -> count(exchange, "error", requesterTags))
					.doFinally(s -> count(exchange, requesterTags));

			if (rSocket instanceof ResponderRSocket) {
				ResponderRSocket socket = (ResponderRSocket) rSocket;
				return socket.requestChannel(payload, flux).log(
						GatewayRSocket.class.getName() + ".request-channel",
						Level.FINEST);
			}
			return rSocket.requestChannel(flux);
		}).doOnNext(s -> count(exchange, "payload", responderTags))
				.doOnError(t -> count(exchange, "error", responderTags))
				.doFinally(s -> count(exchange, responderTags));
	}

	private void count(GatewayExchange exchange, String suffix) {
		count(exchange, suffix, Tags.empty());
	}

	private void count(GatewayExchange exchange, Tags additionalTags) {
		count(exchange, null, additionalTags);
	}

	private void count(GatewayExchange exchange, String suffix, Tags additionalTags) {
		Tags tags = exchange.getTags().and(additionalTags);
		String name = getMetricName(exchange, suffix);
		this.meterRegistry.counter(name, tags).increment();
	}

	private String getMetricName(GatewayExchange exchange) {
		return getMetricName(exchange, null);
	}

	private String getMetricName(GatewayExchange exchange, String suffix) {
		StringBuilder name = new StringBuilder("forward.");
		name.append(exchange.getType().getKey());
		if (StringUtils.hasLength(suffix)) {
			name.append(".");
			name.append(suffix);
		}
		return name.toString();
	}

	@Override
	public Mono<Payload> requestResponse(Payload payload) {
		AtomicReference<Timer.Sample> timer = new AtomicReference<>();
		GatewayExchange exchange = createExchange(REQUEST_RESPONSE, payload);
		return findRSocketOrCreatePending(exchange)
				.flatMap(rSocket -> rSocket.requestResponse(payload))
				.doOnSubscribe(s -> timer.set(Timer.start(meterRegistry)))
				.doOnError(t -> count(exchange, "error"))
				.doFinally(s -> timer.get().stop(meterRegistry
						.timer(getMetricName(exchange), exchange.getTags())));
	}

	@Override
	public Flux<Payload> requestStream(Payload payload) {
		GatewayExchange exchange = createExchange(REQUEST_STREAM, payload);
		return findRSocketOrCreatePending(exchange)
				.flatMapMany(rSocket -> rSocket.requestStream(payload))
				// S N E F
				.doOnNext(s -> count(exchange, "payload"))
				.doOnError(t -> count(exchange, "error"))
				.doFinally(s -> count(exchange, Tags.empty()));
	}

	/**
	 * Attempt to locate target RSocket via filter chain. If not found, create a pending
	 * RSocket.
	 * @param exchange GatewayExchange
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

	/* for testing */ PendingRequestRSocket constructPendingRSocket(
			GatewayExchange exchange) {
		Function<Registry.RegisteredEvent, Mono<Route>> routeFinder = registeredEvent -> getRouteMono(
				registeredEvent, exchange);
		return new PendingRequestRSocket(routeFinder, map -> {
			Tags tags = exchange.getTags().and("responder.id", map.get("id"));
			exchange.setTags(tags);
		});
	}

	protected Mono<Route> getRouteMono(Registry.RegisteredEvent registeredEvent,
			GatewayExchange exchange) {
		return findRoute(exchange)
				.log(PendingRequestRSocket.class.getName() + ".find route pending",
						Level.FINEST)
				// can this be replaced with filter?
				.flatMap(
						route -> matchRoute(route, registeredEvent.getRoutingMetadata()));
	}

	private Mono<Route> findRoute(GatewayExchange exchange) {
		Mono<Route> routeMono;
		/*
		 * if (this.route != null) { //TODO: cache Route? routeMono = Mono.just(route); }
		 * else {
		 */
		routeMono = this.routes.findRoute(exchange);
		// }
		return routeMono;
	}

	private Mono<Route> matchRoute(Route route, Metadata annoucementMetadata) {
		Metadata targetMetadata = route.getTargetMetadata();
		if (targetMetadata.matches(annoucementMetadata)) {
			return Mono.just(route);
		}
		return Mono.empty();
	}

	/**
	 * First locate Route. If found, put route in exchange and execute filter chain. If
	 * successful, locate target RSocket.
	 * @param exchange GatewayExchange.
	 * @return Target RSocket or empty.
	 */
	private Mono<RSocket> findRSocket(GatewayExchange exchange) {
		return this.routes.findRoute(exchange)
				.log(GatewayRSocket.class.getName() + ".find route", Level.FINEST)
				.flatMap(route -> {
					// put route in exchange for later use
					exchange.getAttributes().put(ROUTE_ATTR, route);
					return executeFilterChain(route.getFilters(), exchange)
							.flatMap(success -> {
								LoadBalancedRSocket loadBalancedRSocket = registry
										.getRegistered(exchange.getRoutingMetadata());

								return loadBalancedRSocket.choose();
							}).map(enrichedRSocket -> {
								Metadata metadata = enrichedRSocket.getMetadata();
								Tags tags = exchange.getTags().and("responder.id",
										metadata.get("id"));
								exchange.setTags(tags);
								return enrichedRSocket;
							}).cast(RSocket.class).switchIfEmpty(doOnEmpty(exchange));
				});

		// TODO: deal with connecting to cluster?
	}

	private Mono<RSocket> doOnEmpty(GatewayExchange exchange) {
		if (log.isDebugEnabled()) {
			log.debug("Unable to find destination RSocket for "
					+ exchange.getRoutingMetadata());
		}
		return Mono.empty();
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

		public GatewayRSocket create(Metadata metadata) {
			return new GatewayRSocket(this.registry, this.routes, this.meterRegistry,
					this.properties, metadata);
		}

	}

}
