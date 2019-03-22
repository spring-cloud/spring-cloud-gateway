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

import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.springframework.cloud.gateway.rsocket.route.Route;
import org.springframework.cloud.gateway.rsocket.route.Routes;
import org.springframework.cloud.gateway.rsocket.support.Metadata;

/**
 * Creates routes from RegisteredEvents.
 */
public class RegistryRoutes implements Routes, Consumer<Registry.RegisteredEvent> {

	private static final Log log = LogFactory.getLog(RegistryRoutes.class);

	private Map<String, Route> routes = new ConcurrentHashMap<>();

	@Override
	public Flux<Route> getRoutes() {
		// TODO: sorting
		// TODO: caching
		Collection<Route> routeCollection = routes.values();
		if (log.isDebugEnabled()) {
			log.debug("Found routes: " + routeCollection);
		}
		return Flux.fromIterable(routeCollection);
	}

	@Override
	public void accept(Registry.RegisteredEvent registeredEvent) {
		Metadata routingMetadata = registeredEvent.getRoutingMetadata();
		String id = getId(routingMetadata);

		routes.computeIfAbsent(id, key -> createRoute(id, routingMetadata));
	}

	private String getId(Metadata routingMetadata) {
		String id = routingMetadata.getName();
		if (id == null) {
			id = UUID.randomUUID().toString();
		}
		return id;
	}

	private Route createRoute(String id, Metadata routingMetadata) {
		Route route = Route.builder().id(id).routingMetadata(routingMetadata)
				.predicate(exchange -> {
					// TODO: standard predicates
					// TODO: allow customized predicates
					Metadata incomingRouting = exchange.getRoutingMetadata();
					boolean matches = incomingRouting.getName()
							.equalsIgnoreCase(routingMetadata.getName());
					return Mono.just(matches);
				})
				// TODO: allow customized filters
				.build();

		if (log.isDebugEnabled()) {
			log.debug("Created Route for registered service " + route);
		}

		return route;
	}

}
