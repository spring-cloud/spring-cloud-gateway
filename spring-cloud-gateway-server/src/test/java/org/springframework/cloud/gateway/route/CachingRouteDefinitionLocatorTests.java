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

import java.net.URI;
import java.util.List;
import java.util.concurrent.CountDownLatch;

import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import org.springframework.cloud.gateway.event.RefreshRoutesEvent;

import static org.assertj.core.api.Assertions.assertThat;

public class CachingRouteDefinitionLocatorTests {

	@Test
	public void getRouteDefinitionsWorks() {
		RouteDefinition routeDef1 = routeDef(1);
		RouteDefinition routeDef2 = routeDef(2);
		CachingRouteDefinitionLocator locator = new CachingRouteDefinitionLocator(
				() -> Flux.just(routeDef2, routeDef1));

		List<RouteDefinition> routes = locator.getRouteDefinitions().collectList().block();

		assertThat(routes).containsExactlyInAnyOrder(routeDef1, routeDef2);
	}

	@Test
	public void refreshWorks() {
		RouteDefinition routeDef1 = routeDef(1);
		RouteDefinition routeDef2 = routeDef(2);
		CachingRouteDefinitionLocator locator = new CachingRouteDefinitionLocator(
				new StubRouteDefinitionLocator(Flux.just(routeDef2), Flux.just(routeDef1, routeDef2)));

		List<RouteDefinition> routes = locator.getRouteDefinitions().collectList().block();
		assertThat(routes).containsExactlyInAnyOrder(routeDef2);

		routes = locator.refresh().collectList().block();
		assertThat(routes).containsExactlyInAnyOrder(routeDef1, routeDef2);
	}

	@Test
	public void cacheIsNotClearedOnEvent() {
		RouteDefinition routeDef1 = routeDef(1);
		RouteDefinition routeDef2 = routeDef(2);

		CountDownLatch latch = new CountDownLatch(1);
		CachingRouteDefinitionLocator locator = new CachingRouteDefinitionLocator(
				new StubRouteDefinitionLocator(Flux.just(routeDef1), Flux.defer(() -> {
					try {
						latch.await();
					}
					catch (InterruptedException e) {
						Thread.currentThread().interrupt();
						throw new IllegalStateException(e);
					}
					return Flux.just(routeDef1, routeDef2);
				}).subscribeOn(Schedulers.single())));

		List<RouteDefinition> routes = locator.getRouteDefinitions().collectList().block();
		assertThat(routes).containsExactlyInAnyOrder(routeDef1);

		locator.onApplicationEvent(new RefreshRoutesEvent(this));

		routes = locator.getRouteDefinitions().collectList().block();
		assertThat(routes).containsExactlyInAnyOrder(routeDef1);

		latch.countDown();
	}

	@Test
	public void cacheIsRefreshedInTheBackgroundOnEvent() {
		RouteDefinition routeDef1 = routeDef(1);
		RouteDefinition routeDef2 = routeDef(2);

		CachingRouteDefinitionLocator locator = new CachingRouteDefinitionLocator(new StubRouteDefinitionLocator(
				Flux.just(routeDef1), Flux.defer(() -> Flux.just(routeDef1, routeDef2))));

		List<RouteDefinition> routes = locator.getRouteDefinitions().collectList().block();
		assertThat(routes).containsExactlyInAnyOrder(routeDef1);

		locator.onApplicationEvent(new RefreshRoutesEvent(this));

		List<RouteDefinition> updatedRoutes = locator.getRouteDefinitions().collectList().block();
		assertThat(updatedRoutes).containsExactlyInAnyOrder(routeDef1, routeDef2);
	}

	RouteDefinition routeDef(int id) {
		RouteDefinition def = new RouteDefinition();
		def.setId(String.valueOf(id));
		def.setUri(URI.create("http://localhost/" + id));
		def.setOrder(id);
		return def;
	}

	private static final class StubRouteDefinitionLocator implements RouteDefinitionLocator {

		private final Flux<RouteDefinition> first;

		private final Flux<RouteDefinition> second;

		int i;

		private StubRouteDefinitionLocator(Flux<RouteDefinition> first, Flux<RouteDefinition> second) {
			this.first = first;
			this.second = second;
			i = 0;
		}

		@Override
		public Flux<RouteDefinition> getRouteDefinitions() {
			if (i++ == 0) {
				return first;
			}
			return second;
		}

	}

}
