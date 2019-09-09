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

package org.springframework.cloud.gateway.rsocket.routing;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.springframework.cloud.gateway.rsocket.common.metadata.TagsMetadata;
import org.springframework.cloud.gateway.rsocket.core.GatewayExchange;
import org.springframework.cloud.gateway.rsocket.core.GatewayFilter;
import org.springframework.cloud.gateway.rsocket.route.Route;
import org.springframework.cloud.gateway.rsocket.route.Routes;
import org.springframework.cloud.gateway.rsocket.support.AsyncPredicate;
import org.springframework.core.style.ToStringCreator;

/**
 * View of RoutingTable as Route objects.
 */
public class RoutingTableRoutes
		implements Routes, Consumer<RoutingTable.RegisteredEvent> {

	private static final Log log = LogFactory.getLog(RoutingTableRoutes.class);

	private Map<String, Route> routes = new ConcurrentHashMap<>();

	private final RoutingTable routingTable;

	public RoutingTableRoutes(RoutingTable routingTable) {
		this.routingTable = routingTable;
		this.routingTable.addListener(this);
	}

	@Override
	public Flux<Route> getRoutes() {
		// TODO: sorting?
		// TODO: caching

		Collection<Route> routeCollection = routes.values();
		if (log.isDebugEnabled()) {
			log.debug("Found routes: " + routeCollection);
		}
		return Flux.fromIterable(routeCollection);
	}

	@Override
	public void accept(RoutingTable.RegisteredEvent registeredEvent) {
		TagsMetadata routingMetadata = registeredEvent.getRoutingMetadata();
		String routeId = routingMetadata.getRouteId();

		routes.computeIfAbsent(routeId, key -> createRoute(routeId));
	}

	private Route createRoute(String id) {
		AsyncPredicate<GatewayExchange> predicate = exchange -> {
			// TODO: standard predicates
			// TODO: allow customized predicates
			Set<String> routeIds = routingTable
					.findRouteIds(exchange.getRoutingMetadata());
			return Mono.just(routeIds.contains(id));
		};

		RegistryRoute route = new RegistryRoute(id, predicate);

		if (log.isDebugEnabled()) {
			log.debug("Created Route for registered service " + route);
		}

		return route;
	}

	static class RegistryRoute implements Route {

		final String id;

		final AsyncPredicate<GatewayExchange> predicate;

		RegistryRoute(String id, AsyncPredicate<GatewayExchange> predicate) {
			this.id = id;
			this.predicate = predicate;
		}

		@Override
		public String getId() {
			return this.id;
		}

		@Override
		public AsyncPredicate<GatewayExchange> getPredicate() {
			return this.predicate;
		}

		@Override
		public List<GatewayFilter> getFilters() {
			return Collections.emptyList();
		}

		@Override
		public boolean equals(Object o) {
			if (this == o) {
				return true;
			}
			if (o == null || getClass() != o.getClass()) {
				return false;
			}
			RegistryRoute that = (RegistryRoute) o;
			return Objects.equals(this.id, that.id)
					&& Objects.equals(this.predicate, that.predicate);
		}

		@Override
		public int hashCode() {
			return Objects.hash(this.id, this.predicate);
		}

		@Override
		public String toString() {
			return new ToStringCreator(this).append("id", id)
					.append("predicate", predicate).toString();

		}

	}

}
