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

import java.net.URI;
import java.util.List;

import org.junit.Test;
import reactor.core.publisher.Flux;

import static org.assertj.core.api.Assertions.assertThat;

public class CachingRouteDefinitionLocatorTests {

	@Test
	public void getRouteDefinitionsWorks() {
		RouteDefinition routeDef1 = routeDef(1);
		RouteDefinition routeDef2 = routeDef(2);
		CachingRouteDefinitionLocator locator = new CachingRouteDefinitionLocator(() -> Flux.just(routeDef2, routeDef1));

		List<RouteDefinition> routes = locator.getRouteDefinitions().collectList().block();

		assertThat(routes).containsExactlyInAnyOrder(routeDef1, routeDef2);
	}


	@Test
	public void refreshWorks() {
		RouteDefinition routeDef1 = routeDef(1);
		RouteDefinition routeDef2 = routeDef(2);
		CachingRouteDefinitionLocator locator = new CachingRouteDefinitionLocator(new RouteDefinitionLocator() {
			int i = 0;

			@Override
			public Flux<RouteDefinition> getRouteDefinitions() {
				if (i++ == 0) {
					return Flux.just(routeDef2);
				}
				return Flux.just(routeDef2, routeDef1);
			}
		});

		List<RouteDefinition> routes = locator.getRouteDefinitions().collectList().block();
		assertThat(routes).containsExactlyInAnyOrder(routeDef2);

		routes = locator.refresh().collectList().block();
		assertThat(routes).containsExactlyInAnyOrder(routeDef1, routeDef2);
	}

	RouteDefinition routeDef(int id) {
		RouteDefinition def = new RouteDefinition();
		def.setId(String.valueOf(id));
		def.setUri(URI.create("http://localhost/"+id));
		def.setOrder(id);
		return def;
	}
}
