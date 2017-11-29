/*
 * Copyright 2013-2017 the original author or authors.
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

package org.springframework.cloud.gateway.route;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Predicate;

import org.springframework.cloud.gateway.filter.OrderedGatewayFilter;
import org.springframework.cloud.gateway.filter.factory.GatewayFilters;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.cloud.gateway.filter.GatewayFilter;

import reactor.core.publisher.Flux;

/**
 * @deprecated inject {@link org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder} bean instead
 * @author Spencer Gibb
 */
@Deprecated
public class Routes {

	public static LocatorBuilder locator() {
		return new LocatorBuilder();
	}

	public static class LocatorBuilder {

		private List<Route> routes = new ArrayList<>();

		public PredicateSpec route(String id) {
			return new RouteSpec(this).id(id);
		}

		private void add(Route route) {
			this.routes.add(route);
		}

		LocatorBuilder uri(Route.Builder builder, String uri) {
			Route route = builder.uri(uri).build();
			routes.add(route);
			return this;
		}

		LocatorBuilder uri(Route.Builder builder, URI uri) {
			Route route = builder.uri(uri).build();
			routes.add(route);
			return this;
		}

		public RouteLocator build() {
			return () -> Flux.fromIterable(this.routes);
		}

	}

	public static class RouteSpec {
		private final Route.Builder builder = Route.builder();
		private final LocatorBuilder locatorBuilder;

		private RouteSpec(LocatorBuilder locatorBuilder) {
			this.locatorBuilder = locatorBuilder;
		}

		public PredicateSpec id(String id) {
			this.builder.id(id);
			return predicateBuilder();
		}

		private PredicateSpec predicateBuilder() {
			return new PredicateSpec(this.builder, this.locatorBuilder);
		}

	}

	public static class PredicateSpec {

		private final Route.Builder builder;
		private LocatorBuilder locatorBuilder;

		private PredicateSpec(Route.Builder builder, LocatorBuilder locatorBuilder) {
			this.builder = builder;
			this.locatorBuilder = locatorBuilder;
		}

		/* TODO: has and, or & negate of Predicate with terminal andFilters()?
		public RoutePredicateBuilder predicate() {
		}
		// this goes in new class
		public RoutePredicateBuilder host(String pattern) {
			Predicate<ServerWebExchange> predicate = RoutePredicates.host(pattern);
		}*/

		public PredicateSpec order(int order) {
			this.builder.order(order);
			return this;
		}

		public GatewayFilterSpec predicate(Predicate<ServerWebExchange> predicate) {
			this.builder.predicate(predicate);
			return gatewayFilterBuilder();
		}

		private GatewayFilterSpec gatewayFilterBuilder() {
			return new GatewayFilterSpec(this.builder, this.locatorBuilder);
		}

		public LocatorBuilder uri(String uri) {
			return this.locatorBuilder.uri(this.builder, uri);
		}

		public LocatorBuilder uri(URI uri) {
			return this.locatorBuilder.uri(this.builder, uri);
		}
	}

	public static class GatewayFilterSpec {
		private Route.Builder builder;
		private LocatorBuilder locatorBuilder;

		public GatewayFilterSpec(Route.Builder routeBuilder, LocatorBuilder locatorBuilder) {
			this.builder = routeBuilder;
			this.locatorBuilder = locatorBuilder;
		}

		public GatewayFilterSpec gatewayFilters(List<GatewayFilter> gatewayFilters) {
			this.addAll(gatewayFilters);
			return this;
		}

		public GatewayFilterSpec add(GatewayFilter gatewayFilter) {
			return this.filter(gatewayFilter);
		}

		public GatewayFilterSpec filter(GatewayFilter gatewayFilter) {
			return this.filter(gatewayFilter, 0);
		}

		public GatewayFilterSpec filter(GatewayFilter gatewayFilter, int order) {
			this.builder.add(new OrderedGatewayFilter(gatewayFilter, order));
			return this;
		}

		public GatewayFilterSpec addAll(Collection<GatewayFilter> gatewayFilters) {
			this.builder.addAll(gatewayFilters);
			return this;
		}

		public GatewayFilterSpec addResponseHeader(String headerName, String headerValue) {
			return add(GatewayFilters.addResponseHeader(headerName, headerValue));
		}

		public LocatorBuilder uri(String uri) {
			return this.locatorBuilder.uri(this.builder, uri);
		}

		public LocatorBuilder uri(URI uri) {
			return this.locatorBuilder.uri(this.builder, uri);
		}
	}

}
