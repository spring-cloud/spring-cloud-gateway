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
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.MonoProcessor;

import org.springframework.cloud.gateway.rsocket.filter.RSocketFilter.Success;
import org.springframework.cloud.gateway.rsocket.registry.Registry.RegisteredEvent;
import org.springframework.cloud.gateway.rsocket.route.Route;
import org.springframework.cloud.gateway.rsocket.route.Routes;
import org.springframework.cloud.gateway.rsocket.support.Metadata;

import static org.springframework.cloud.gateway.rsocket.server.GatewayExchange.ROUTE_ATTR;

public class PendingRequestRSocket extends AbstractRSocket implements Consumer<RegisteredEvent> {

	private final Routes routes;
	private final GatewayExchange pendingExchange;
	private final MonoProcessor<RSocket> processor;

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
	 * If it matches, execute filter chain.
	 * If chain is successful, send registered RSocket to processor.
	 * @param registeredEvent
	 */
	@Override
	public void accept(RegisteredEvent registeredEvent) {
		findRoute()
				.log("find route pending", Level.FINE)
				// can this be replaced with filter?
				.map(route -> {
					if (!pendingExchange.getAttributes().containsKey(ROUTE_ATTR)) {
						pendingExchange.getAttributes().put(ROUTE_ATTR, route);
					}
					return executeFilterChain(route, registeredEvent.getRoutingMetadata());
				})
				.subscribe(success -> {
					this.processor.onNext(registeredEvent.getRSocket());
					this.processor.onComplete();
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

	private Mono<Success> executeFilterChain(Route route, Map<String, String> annoucementMetadata) {
		Map<String, String> targetMetadata = route.getTargetMetadata();
		if (Metadata.matches(targetMetadata, annoucementMetadata)) {
			return GatewayFilterChain.executeFilterChain(route.getFilters(), pendingExchange);
		}
		return Mono.empty();
	}

	@Override
	public Mono<Void> fireAndForget(Payload payload) {
		return processor
				.log("pending-request-faf", Level.FINE)
				.flatMap(rsocket -> rsocket.fireAndForget(payload));
	}

	@Override
	public Mono<Payload> requestResponse(Payload payload) {
		return processor
				.log("pending-request-rr", Level.FINE)
				.flatMap(rsocket -> rsocket.requestResponse(payload));
	}

	@Override
	public Flux<Payload> requestStream(Payload payload) {
		return processor
				.log("pending-request-rs", Level.FINE)
				.flatMapMany(rsocket -> rsocket.requestStream(payload));
	}

	@Override
	public Flux<Payload> requestChannel(Publisher<Payload> payloads) {
		return processor
				.log("pending-request-rc", Level.FINE)
				.flatMapMany(rsocket -> rsocket.requestChannel(payloads));
	}
}
