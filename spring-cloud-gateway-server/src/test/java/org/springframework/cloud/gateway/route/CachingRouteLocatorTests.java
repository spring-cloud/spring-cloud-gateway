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

package org.springframework.cloud.gateway.route;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.Ignore;
import org.junit.Test;
import reactor.core.publisher.Flux;

import org.springframework.cloud.gateway.event.RefreshRoutesEvent;
import org.springframework.cloud.gateway.event.RefreshRoutesResultEvent;

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

	@Test
	@Ignore // FIXME: 3.0.0
	public void refreshWorksWhenFirstRefreshSuccessAndOtherError() throws InterruptedException {
		Route route1 = route(1);
		Route route2 = route(2);

		CachingRouteLocator locator = new CachingRouteLocator(new RouteLocator() {
			int i = 0;

			@Override
			public Flux<Route> getRoutes() {
				if (i == 0) {
					i++;
					return Flux.just(route1);
				}
				else if (i == 1) {
					i++;
					return Flux.just(route2).map(route -> {
						throw new RuntimeException("in chain.");
					});
				}
				else if (i == 2) {
					i++;
					throw new RuntimeException("call getRoutes error.");
				}
				return Flux.just(route2);
			}
		});

		List<Route> routes = locator.getRoutes().collectList().block();
		assertThat(routes).containsExactly(route1);

		List<RefreshRoutesResultEvent> resultEvents = new ArrayList<>();

		waitUntilRefreshFinished(locator, resultEvents);
		assertThat(resultEvents).hasSize(1);
		assertThat(resultEvents.get(0).getThrowable().getCause().getMessage()).isEqualTo("in chain.");
		assertThat(resultEvents.get(0).isSuccess()).isEqualTo(false);
		assertThat(locator.getRoutes().collectList().block()).containsExactly(route1);

		waitUntilRefreshFinished(locator, resultEvents);
		assertThat(resultEvents).hasSize(2);
		assertThat(resultEvents.get(1).getThrowable().getMessage()).isEqualTo("call getRoutes error.");
		assertThat(resultEvents.get(1).isSuccess()).isEqualTo(false);
		assertThat(locator.getRoutes().collectList().block()).containsExactly(route1);

		waitUntilRefreshFinished(locator, resultEvents);
		assertThat(resultEvents).hasSize(3);
		assertThat(resultEvents.get(2).isSuccess()).isEqualTo(true);
		assertThat(locator.getRoutes().collectList().block()).containsExactly(route2);

	}

	private void waitUntilRefreshFinished(CachingRouteLocator locator, List<RefreshRoutesResultEvent> resultEvents)
			throws InterruptedException {
		CountDownLatch cdl = new CountDownLatch(1);
		locator.setApplicationEventPublisher(o -> {
			resultEvents.add((RefreshRoutesResultEvent) o);
			cdl.countDown();
		});
		locator.onApplicationEvent(new RefreshRoutesEvent(this));

		assertThat(cdl.await(5, TimeUnit.SECONDS)).isTrue();
	}

	Route route(int id) {
		return Route.async().id(String.valueOf(id)).uri("http://localhost/" + id).order(id).predicate(exchange -> true)
				.build();
	}

}
