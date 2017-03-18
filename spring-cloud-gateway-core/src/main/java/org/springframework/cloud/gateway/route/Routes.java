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

import org.springframework.cloud.gateway.filter.factory.WebFilterFactories;
import org.springframework.web.reactive.function.server.RequestPredicate;
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

		public RouteBuilder route(String id) {
			return new RouteBuilder(this).id(id);
		}

		private void add(Route route) {
			this.routes.add(route);
		}

		public RouteLocator build() {
			return () -> Flux.fromIterable(this.routes);
		}

	}

	public static class RouteBuilder {
		private final Route.Builder builder = Route.builder();
		private final LocatorBuilder locatorBuilder;

		private RouteBuilder(LocatorBuilder locatorBuilder) {
			this.locatorBuilder = locatorBuilder;
		}

		public RouteBuilder id(String id) {
			this.builder.id(id);
			return this;
		}

		public PredicateBuilder uri(String uri) {
			this.builder.uri(uri);
			return predicateBuilder();
		}

		public PredicateBuilder uri(URI uri) {
			this.builder.uri(uri);
			return predicateBuilder();
		}

		private PredicateBuilder predicateBuilder() {
			return new PredicateBuilder(this.builder, this.locatorBuilder);
		}

	}

	public static class PredicateBuilder {

		private final Route.Builder routeBuilder;
		private LocatorBuilder locatorBuilder;

		private PredicateBuilder(Route.Builder routeBuilder, LocatorBuilder locatorBuilder) {
			this.routeBuilder = routeBuilder;
			this.locatorBuilder = locatorBuilder;
		}

		/* TODO: has and, or & negate of RequestPredicate with terminal filters()?
		public RequestPredicateBuilder host(String pattern) {
			RequestPredicate requestPredicate = GatewayRequestPredicates.host(pattern);
		}*/

		public WebFilterBuilder predicate(RequestPredicate predicate) {
			this.routeBuilder.requestPredicate(predicate);
			return webFilterBuilder();
		}

		private WebFilterBuilder webFilterBuilder() {
			return new WebFilterBuilder(this.routeBuilder, this.locatorBuilder);
		}

	}

	public static class WebFilterBuilder {
		private Route.Builder builder;
		private LocatorBuilder locatorBuilder;

		public WebFilterBuilder(Route.Builder routeBuilder, LocatorBuilder locatorBuilder) {
			this.builder = routeBuilder;
			this.locatorBuilder = locatorBuilder;
		}

		public WebFilterBuilder webFilters(List<WebFilter> webFilters) {
			this.builder.webFilters(webFilters);
			return this;
		}

		public WebFilterBuilder add(WebFilter webFilter) {
			this.builder.add(webFilter);
			return this;
		}

		public WebFilterBuilder addAll(Collection<WebFilter> webFilters) {
			this.builder.addAll(webFilters);
			return this;
		}

		public WebFilterBuilder addResponseHeader(String headerName, String headerValue) {
			return add(WebFilterFactories.addResponseHeader(headerName, headerValue));
		}

		public LocatorBuilder and() {
			Route route = this.builder.build();
			this.locatorBuilder.add(route);
			return this.locatorBuilder;
		}
	}

}
