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
 */

package org.springframework.cloud.gateway.route.builder;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;

import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.context.ConfigurableApplicationContext;

import reactor.core.publisher.Flux;

public class RouteLocatorBuilder {

	private ConfigurableApplicationContext context;

	public RouteLocatorBuilder(ConfigurableApplicationContext context) {
		this.context = context;
	}

	public Builder routes() {
		return new Builder(context);
	}

	public static class Builder {

		private List<Route> routes = new ArrayList<>();
		private ConfigurableApplicationContext context;

		public Builder(ConfigurableApplicationContext context) {
			this.context = context;
		}

		public Builder route(String id, Function<PredicateSpec, Builder> fn) {
			return fn.apply(new RouteSpec(this).id(id));
		}

		public Builder route(Function<PredicateSpec, Builder> fn) {
			return fn.apply(new RouteSpec(this).randomId());
		}

		private void add(Route route) {
			this.routes.add(route);
		}

		Builder uri(Route.Builder builder, String uri) {
			Route route = builder.uri(uri).build();
			add(route);
			return this;
		}

		Builder uri(Route.Builder builder, URI uri) {
			Route route = builder.uri(uri).build();
			add(route);
			return this;
		}

		public RouteLocator build() {
			return () -> Flux.fromIterable(this.routes);
		}

		ConfigurableApplicationContext getContext() {
			return context;
		}
	}


	public static class RouteSpec {
		private final Route.Builder routeBuilder = Route.builder();
		private final Builder builder;

		RouteSpec(Builder builder) {
			this.builder = builder;
		}

		public PredicateSpec id(String id) {
			this.routeBuilder.id(id);
			return predicateBuilder();
		}

		public PredicateSpec randomId() {
			return id(UUID.randomUUID().toString());
		}

		private PredicateSpec predicateBuilder() {
			return new PredicateSpec(this.routeBuilder, this.builder);
		}

	}


}
