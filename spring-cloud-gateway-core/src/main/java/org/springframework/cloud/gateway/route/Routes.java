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

import org.springframework.cloud.gateway.filter.factory.WebFilterFactories;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;

import reactor.core.publisher.Flux;

/**
 * @author Spencer Gibb
 */
public class Routes {

	public static LocatorBuilder locator() {
		return new LocatorBuilder();
	}

	public static class LocatorBuilder {

		private List<Route> routes = new ArrayList<>();

		public RouteSpec route(String id) {
			return new RouteSpec(this).id(id);
		}

		private void add(Route route) {
			this.routes.add(route);
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

		public RouteSpec id(String id) {
			this.builder.id(id);
			return this;
		}

		public PredicateSpec uri(String uri) {
			this.builder.uri(uri);
			return predicateBuilder();
		}

		public PredicateSpec uri(URI uri) {
			this.builder.uri(uri);
			return predicateBuilder();
		}

		private PredicateSpec predicateBuilder() {
			return new PredicateSpec(this.builder, this.locatorBuilder);
		}

	}

	public static class PredicateSpec {

		private final Route.Builder routeBuilder;
		private LocatorBuilder locatorBuilder;

		private PredicateSpec(Route.Builder routeBuilder, LocatorBuilder locatorBuilder) {
			this.routeBuilder = routeBuilder;
			this.locatorBuilder = locatorBuilder;
		}

		/* TODO: has and, or & negate of Predicate with terminal andFilters()?
		public RoutePredicateBuilder predicate() {
		}
		// this goes in new class
		public RoutePredicateBuilder host(String pattern) {
			Predicate<ServerWebExchange> predicate = RoutePredicates.host(pattern);
		}*/

		public WebFilterSpec predicate(Predicate<ServerWebExchange> predicate) {
			this.routeBuilder.predicate(predicate);
			return webFilterBuilder();
		}

		private WebFilterSpec webFilterBuilder() {
			return new WebFilterSpec(this.routeBuilder, this.locatorBuilder);
		}

	}

	public static class WebFilterSpec {
		private Route.Builder builder;
		private LocatorBuilder locatorBuilder;

		public WebFilterSpec(Route.Builder routeBuilder, LocatorBuilder locatorBuilder) {
			this.builder = routeBuilder;
			this.locatorBuilder = locatorBuilder;
		}

		public WebFilterSpec webFilters(List<WebFilter> webFilters) {
			this.builder.webFilters(webFilters);
			return this;
		}

		public WebFilterSpec add(WebFilter webFilter) {
			this.builder.add(webFilter);
			return this;
		}

		public WebFilterSpec addAll(Collection<WebFilter> webFilters) {
			this.builder.addAll(webFilters);
			return this;
		}

		public WebFilterSpec addResponseHeader(String headerName, String headerValue) {
			return add(WebFilterFactories.addResponseHeader(headerName, headerValue));
		}

		// TODO: build()?
		public LocatorBuilder and() {
			Route route = this.builder.build();
			this.locatorBuilder.add(route);
			return this.locatorBuilder;
		}
	}

}
