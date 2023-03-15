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

import reactor.core.publisher.Flux;
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
public class GatewayControllerEndpoint extends AbstractGatewayControllerEndpoint {

	public GatewayControllerEndpoint(List<GlobalFilter> globalFilters, List<GatewayFilterFactory> gatewayFilters,
			List<RoutePredicateFactory> routePredicates, RouteDefinitionWriter routeDefinitionWriter,
			RouteLocator routeLocator, RouteDefinitionLocator routeDefinitionLocator) {
		super(routeDefinitionLocator, globalFilters, gatewayFilters, routePredicates, routeDefinitionWriter,
				routeLocator);
	}

	@GetMapping("/routedefinitions")
	public Flux<RouteDefinition> routesdef() {
		return this.routeDefinitionLocator.getRouteDefinitions();
	}

	// TODO: Flush out routes without a definition
	@GetMapping("/routes")
	public Flux<Map<String, Object>> routes() {
		return this.routeLocator.getRoutes().map(this::serialize);
	}

	Map<String, Object> serialize(Route route) {
		HashMap<String, Object> r = new HashMap<>();
		r.put("route_id", route.getId());
		r.put("uri", route.getUri().toString());
		r.put("order", route.getOrder());
		r.put("predicate", route.getPredicate().toString());
		if (!CollectionUtils.isEmpty(route.getMetadata())) {
			r.put("metadata", route.getMetadata());
		}

		ArrayList<String> filters = new ArrayList<>();

		for (int i = 0; i < route.getFilters().size(); i++) {
			GatewayFilter gatewayFilter = route.getFilters().get(i);
			filters.add(gatewayFilter.toString());
		}

		r.put("filters", filters);
		return r;
	}

	@GetMapping("/routes/{id}")
	public Mono<ResponseEntity<Map<String, Object>>> route(@PathVariable String id) {
		// @formatter:off
		return this.routeLocator.getRoutes()
				.filter(route -> route.getId().equals(id))
				.singleOrEmpty()
				.map(this::serialize)
				.map(ResponseEntity::ok)
				.switchIfEmpty(Mono.just(ResponseEntity.notFound().build()));
		// @formatter:on
	}

}
