/*
 * Copyright 2013-2018 the original author or authors.
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
 *
 */

package org.springframework.cloud.gateway.route;

import java.util.List;

import org.junit.Test;
import reactor.core.publisher.Flux;

import static org.assertj.core.api.Assertions.assertThat;

public class CachingRouteLocatorTests {

	@Test
	public void getRoutesWorks() {
		Route route1 = route(1);
		Route route2 = route(2);
		CachingRouteLocator locator = new CachingRouteLocator(() -> Flux.just(route2, route1));

		List<Route> routes = locator.getRoutes().collectList().block();

		assertThat(routes).containsExactly(route1, route2);
	}


	@Test
	public void refreshWorks() {
		Route route1 = route(1);
		Route route2 = route(2);
		CachingRouteLocator locator = new CachingRouteLocator(new RouteLocator() {
			int i = 0;

			@Override
			public Flux<Route> getRoutes() {
				if (i++ == 0) {
					return Flux.just(route2);
				}
				return Flux.just(route2, route1);
			}
		});

		List<Route> routes = locator.getRoutes().collectList().block();
		assertThat(routes).containsExactly(route2);

		routes = locator.refresh().collectList().block();
		assertThat(routes).containsExactly(route1, route2);
	}

	Route route(int id) {
		return Route.async().id(String.valueOf(id))
				.uri("http://localhost/"+id)
				.order(id)
				.predicate(exchange -> true).build();
	}
}
