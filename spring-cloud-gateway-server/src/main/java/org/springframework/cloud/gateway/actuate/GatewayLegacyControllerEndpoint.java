/*
 * Copyright 2013-2020 the original author or authors.
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

package org.springframework.cloud.gateway.actuate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import reactor.core.publisher.Mono;

import org.springframework.boot.actuate.endpoint.web.annotation.RestControllerEndpoint;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.filter.factory.GatewayFilterFactory;
import org.springframework.cloud.gateway.handler.predicate.RoutePredicateFactory;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.route.RouteDefinition;
import org.springframework.cloud.gateway.route.RouteDefinitionLocator;
import org.springframework.cloud.gateway.route.RouteDefinitionWriter;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.http.ResponseEntity;
import org.springframework.util.CollectionUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * @author Spencer Gibb
 */
@RestControllerEndpoint(id = "gateway", enableByDefault = false)
public class GatewayLegacyControllerEndpoint extends AbstractGatewayControllerEndpoint {

	public GatewayLegacyControllerEndpoint(RouteDefinitionLocator routeDefinitionLocator,
			List<GlobalFilter> globalFilters, List<GatewayFilterFactory> gatewayFilterFactories,
			List<RoutePredicateFactory> routePredicates, RouteDefinitionWriter routeDefinitionWriter,
			RouteLocator routeLocator) {
		super(routeDefinitionLocator, globalFilters, gatewayFilterFactories, routePredicates, routeDefinitionWriter,
				routeLocator);
	}

	@GetMapping("/routes")
	public Mono<List<Map<String, Object>>> routes() {
		Mono<Map<String, RouteDefinition>> routeDefs = this.routeDefinitionLocator.getRouteDefinitions()
				.collectMap(RouteDefinition::getId);
		Mono<List<Route>> routes = this.routeLocator.getRoutes().collectList();
		return Mono.zip(routeDefs, routes).map(tuple -> {
			Map<String, RouteDefinition> defs = tuple.getT1();
			List<Route> routeList = tuple.getT2();
			List<Map<String, Object>> allRoutes = new ArrayList<>();

			routeList.forEach(route -> {
				HashMap<String, Object> r = new HashMap<>();
				r.put("route_id", route.getId());
				r.put("order", route.getOrder());

				if (defs.containsKey(route.getId())) {
					r.put("route_definition", defs.get(route.getId()));
				}
				else {
					HashMap<String, Object> obj = new HashMap<>();

					obj.put("predicate", route.getPredicate().toString());

					if (!route.getFilters().isEmpty()) {
						ArrayList<String> filters = new ArrayList<>();
						for (GatewayFilter filter : route.getFilters()) {
							filters.add(filter.toString());
						}

						obj.put("filters", filters);
					}

					if (!CollectionUtils.isEmpty(route.getMetadata())) {
						obj.put("metadata", route.getMetadata());
					}

					if (!obj.isEmpty()) {
						r.put("route_object", obj);
					}
				}
				allRoutes.add(r);
			});

			return allRoutes;
		});
	}

	@GetMapping("/routes/{id}")
	public Mono<ResponseEntity<RouteDefinition>> route(@PathVariable String id) {
		// TODO: missing RouteLocator
		return this.routeDefinitionLocator.getRouteDefinitions().filter(route -> route.getId().equals(id))
				.singleOrEmpty().map(ResponseEntity::ok).switchIfEmpty(Mono.just(ResponseEntity.notFound().build()));
	}

}
