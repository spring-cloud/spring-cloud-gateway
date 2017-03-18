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

import org.springframework.web.reactive.function.server.RequestPredicate;
import org.springframework.web.server.WebFilter;
import reactor.core.publisher.Flux;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

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

		public RouteBuilder uri(String uri) {
			this.builder.uri(uri);
			return this;
		}

		public RouteBuilder uri(URI uri) {
			this.builder.uri(uri);
			return this;
		}

		public RouteBuilder predicate(RequestPredicate predicate) {
			this.builder.requestPredicate(predicate);
			return this;
		}

		//TODO: maybe add WebFilterBuilder so no need to import WebFilterFactories?
		public RouteBuilder webFilters(List<WebFilter> webFilters) {
			this.builder.webFilters(webFilters);
			return this;
		}

		public RouteBuilder add(WebFilter webFilter) {
			this.builder.add(webFilter);
			return this;
		}

		public RouteBuilder addAll(Collection<WebFilter> webFilters) {
			this.builder.addAll(webFilters);
			return this;
		}

		public LocatorBuilder and() {
			Route route = this.builder.build();
			this.locatorBuilder.add(route);
			return this.locatorBuilder;
		}
	}

}
