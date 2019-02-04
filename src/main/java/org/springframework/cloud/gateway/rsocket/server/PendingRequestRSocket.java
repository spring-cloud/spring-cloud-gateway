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
import java.util.function.Consumer;
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
import reactor.core.publisher.MonoProcessor;
import reactor.util.function.Tuple2;

import org.springframework.cloud.gateway.rsocket.filter.RSocketFilter.Success;
import org.springframework.cloud.gateway.rsocket.registry.Registry.RegisteredEvent;
import org.springframework.cloud.gateway.rsocket.route.Route;
import org.springframework.cloud.gateway.rsocket.route.Routes;
import org.springframework.cloud.gateway.rsocket.support.Metadata;

import static org.springframework.cloud.gateway.rsocket.server.GatewayExchange.FIRST_PAYLOAD_ATTR;
import static org.springframework.cloud.gateway.rsocket.server.GatewayExchange.ROUTE_ATTR;
import static org.springframework.cloud.gateway.rsocket.server.GatewayExchange.Type.REQUEST_STREAM;
import static org.springframework.cloud.gateway.rsocket.server.GatewayFilterChain.executeFilterChain;

public class PendingRequestRSocket extends AbstractRSocket implements Consumer<RegisteredEvent> {

	private static final Log log = LogFactory.getLog(PendingRequestRSocket.class);

	private final Routes routes;
	private final GatewayExchange pendingExchange;
	private final MonoProcessor<RSocket> processor;
	private Disposable subscriptionDisposable;

	public PendingRequestRSocket(Routes routes, GatewayExchange pendingExchange) {
		this(routes, pendingExchange, MonoProcessor.create());
	}

	/* for testing */ PendingRequestRSocket(Routes routes, GatewayExchange pendingExchange, MonoProcessor<RSocket> processor) {
		this.routes = routes;
		this.pendingExchange = pendingExchange;
		this.processor = processor;
	}

	/**
	 * Find route (if needed) using pendingExchange.
	 * If found, see if the route target matches the registered service.
	 * If it matches, send registered RSocket to processor.
	 * Then execute normal filter chain. If filter chain is successful, execute request.
	 * @param registeredEvent
	 */
	@Override
	public void accept(RegisteredEvent registeredEvent) {
		findRoute()
				.log("find route pending", Level.FINE)
				// can this be replaced with filter?
				.flatMap(route -> {
					if (!pendingExchange.getAttributes().containsKey(ROUTE_ATTR)) {
						if (log.isDebugEnabled()) {
							log.debug("route not in exchange, adding.");
						}
						pendingExchange.getAttributes().put(ROUTE_ATTR, route);
					}
					return matchRoute(route, registeredEvent.getRoutingMetadata());
				})
				.subscribe(route -> {
					this.processor.onNext(registeredEvent.getRSocket());
					this.processor.onComplete();
					if (this.subscriptionDisposable != null) {
						this.subscriptionDisposable.dispose();
					}
				});
	}

	private Mono<Route> findRoute() {
		Mono<Route> routeMono;
		if (pendingExchange.getAttributes().containsKey(ROUTE_ATTR)) {
			Route r = pendingExchange.getRequiredAttribute(ROUTE_ATTR);
			routeMono = Mono.just(r);
		} else {
			routeMono = this.routes.findRoute(pendingExchange);
		}
		return routeMono;
	}

	private Mono<Route> matchRoute(Route route, Map<String, String> annoucementMetadata) {
		Map<String, String> targetMetadata = route.getTargetMetadata();
		if (Metadata.matches(targetMetadata, annoucementMetadata)) {
			return Mono.just(route);
		}
		return Mono.empty();
	}

	@Override
	public Mono<Void> fireAndForget(Payload payload) {
		return processor("pending-request-faf", payload)
				.flatMap(tuple -> tuple.getT1().fireAndForget(payload));
	}

	@Override
	public Mono<Payload> requestResponse(Payload payload) {
		return processor("pending-request-rr", payload)
				.flatMap(tuple -> tuple.getT1().requestResponse(payload));
	}

	@Override
	public Flux<Payload> requestStream(Payload payload) {
		return processor("pending-request-rs", payload)
				.flatMapMany(tuple -> tuple.getT1().requestStream(payload));
	}

	@Override
	public Flux<Payload> requestChannel(Publisher<Payload> payloads) {
		//TODO: remove this when new RSocketServer method for requestChannel is here
		// otherwise there is a dual subscription error
		Payload firstPaylad = pendingExchange.getRequiredAttribute(FIRST_PAYLOAD_ATTR);
		return processor("pending-request-rc", firstPaylad)
				.flatMapMany(tuple -> tuple.getT1().requestChannel(payloads));
	}

	/**
	 * After processor receives onNext signal, get route from exchange attrs,
	 * create a new exchange from payload. Copy exchange attrs.
	 * Execute filter chain, if successful, execute request.
	 * @param logCategory
	 * @param payload
	 * @return
	 */
	protected Mono<Tuple2<RSocket, Success>> processor(String logCategory, Payload payload) {
		return processor
				.log(logCategory, Level.FINE)
				.flatMap(rSocket -> {
					Route route = pendingExchange.getAttribute(ROUTE_ATTR);
					GatewayExchange exchange = GatewayExchange.fromPayload(REQUEST_STREAM, payload);
					exchange.getAttributes().putAll(pendingExchange.getAttributes());
					return Mono.just(rSocket).zipWith(executeFilterChain(route.getFilters(), exchange));
				});

	}

	public void setSubscriptionDisposable(Disposable subscriptionDisposable) {
		this.subscriptionDisposable = subscriptionDisposable;
	}
}
