/*
 * Copyright 2013-2019 the original author or authors.
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

package org.springframework.cloud.gateway.rsocket.routing;

import java.util.HashSet;

import io.rsocket.RSocket;
import org.junit.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import org.springframework.cloud.gateway.rsocket.common.metadata.Forwarding;
import org.springframework.cloud.gateway.rsocket.common.metadata.TagsMetadata;
import org.springframework.cloud.gateway.rsocket.core.GatewayExchange;
import org.springframework.cloud.gateway.rsocket.route.Route;
import org.springframework.cloud.gateway.rsocket.routing.RoutingTable.RegisteredEvent;
import org.springframework.cloud.gateway.rsocket.routing.RoutingTable.RouteEntry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.cloud.gateway.rsocket.core.GatewayExchange.Type.REQUEST_RESPONSE;

public class RoutingTableRoutesTests {

	@Test
	public void routesAreBuilt() {
		RoutingTable routingTable = mock(RoutingTable.class);
		RoutingTableRoutes routes = new RoutingTableRoutes(routingTable);

		HashSet<String> routeIds = new HashSet<>();
		routeIds.add("2");
		when(routingTable.findRouteIds(any(TagsMetadata.class))).thenReturn(routeIds);
		addRoute(routes, "1");
		addRoute(routes, "2");
		addRoute(routes, "3");

		Forwarding forwarding = Forwarding.of(1L).routeId("2").build();
		Mono<Route> routeMono = routes
				.findRoute(new GatewayExchange(REQUEST_RESPONSE, forwarding));

		StepVerifier.create(routeMono).consumeNextWith(route -> {
			assertThat(route).isNotNull().extracting(Route::getId).isEqualTo("2");
		}).verifyComplete();
	}

	void addRoute(RoutingTableRoutes routes, String routeId) {
		TagsMetadata tagsMetadata = TagsMetadata.builder().routeId(routeId).build();

		RSocket rsocket = mock(RSocket.class);
		routes.accept(new RegisteredEvent(new RouteEntry(rsocket, tagsMetadata)));
	}

}
