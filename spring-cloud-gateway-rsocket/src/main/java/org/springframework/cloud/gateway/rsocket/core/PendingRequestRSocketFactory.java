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

package org.springframework.cloud.gateway.rsocket.core;

import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.logging.Level;

import io.micrometer.core.instrument.Tags;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import reactor.core.Disposable;
import reactor.core.publisher.Mono;

import org.springframework.cloud.gateway.rsocket.metadata.TagsMetadata;
import org.springframework.cloud.gateway.rsocket.route.Route;
import org.springframework.cloud.gateway.rsocket.route.Routes;
import org.springframework.cloud.gateway.rsocket.routing.RoutingTable;
import org.springframework.messaging.rsocket.MetadataExtractor;

public class PendingRequestRSocketFactory {

	private static final Log log = LogFactory.getLog(PendingRequestRSocket.class);

	private final RoutingTable routingTable;

	private final Routes routes;

	private final MetadataExtractor metadataExtractor;

	public PendingRequestRSocketFactory(RoutingTable routingTable, Routes routes,
			MetadataExtractor metadataExtractor) {
		this.routingTable = routingTable;
		this.routes = routes;
		this.metadataExtractor = metadataExtractor;
	}

	public Mono<PendingRequestRSocket> create(GatewayExchange exchange) {
		if (log.isDebugEnabled()) {
			log.debug("creating pending RSocket for " + exchange.getRoutingMetadata());
		}
		PendingRequestRSocket pending = constructPendingRSocket(exchange);
		Disposable disposable = this.routingTable.addListener(pending);
		pending.setSubscriptionDisposable(disposable);
		return Mono.just(pending);
	}

	protected PendingRequestRSocket constructPendingRSocket(GatewayExchange exchange) {
		Function<RoutingTable.RegisteredEvent, Mono<Route>> routeFinder = registeredEvent -> getRouteMono(
				registeredEvent, exchange);
		Consumer<TagsMetadata> tagsMetadataConsumer = tagsMetadata -> {
			Tags tags = exchange.getTags().and("responder.id", tagsMetadata.getRouteId());
			exchange.setTags(tags);
		};
		return new PendingRequestRSocket(metadataExtractor, routeFinder,
				tagsMetadataConsumer);
	}

	/**
	 * Finds routes using exchange of original request that created pending RSocket.
	 * @param registeredEvent newly registered event
	 * @param exchange from original request
	 * @return route if route matches
	 */
	protected Mono<Route> getRouteMono(RoutingTable.RegisteredEvent registeredEvent,
			GatewayExchange exchange) {
		return this.routes.findRoute(exchange)
				.log(PendingRequestRSocket.class.getName() + ".find route pending",
						Level.FINEST)
				// TODO: can this be replaced with filter?
				.flatMap(
						route -> matchRoute(route, registeredEvent.getRoutingMetadata()));
	}

	/**
	 * Matches route found using original exchange with routeIds from recently registered
	 * routes.
	 * @param route route found using original exchange.
	 * @param tagsMetadata tags from recent registration.
	 * @return
	 */
	private Mono<Route> matchRoute(Route route, TagsMetadata tagsMetadata) {
		Set<String> routeIds = this.routingTable.findRouteIds(tagsMetadata);
		if (routeIds.contains(route.getId())) {
			return Mono.just(route);
		}
		return Mono.empty();
	}

}
