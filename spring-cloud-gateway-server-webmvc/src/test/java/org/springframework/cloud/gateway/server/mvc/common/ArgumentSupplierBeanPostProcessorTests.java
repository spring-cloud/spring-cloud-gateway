/*
 * Copyright 2013-present the original author or authors.
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

package org.springframework.cloud.gateway.server.mvc.common;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.cloud.gateway.server.mvc.handler.GatewayRouterFunctions.route;
import static org.springframework.cloud.gateway.server.mvc.handler.HandlerFunctions.http;
import static org.springframework.cloud.gateway.server.mvc.predicate.GatewayRequestPredicates.weight;

public class ArgumentSupplierBeanPostProcessorTests {

	@Test
	public void publishesEventForEachWeightRouteInSingleRouterFunction() {
		List<Object> events = new ArrayList<>();
		ArgumentSupplierBeanPostProcessor processor = new ArgumentSupplierBeanPostProcessor(events::add);

		RouterFunction<ServerResponse> routerFunction = route("weight_high").route(weight("group1", 8), http())
			.build()
			.and(route("weight_low").route(weight("group1", 2), http()).build());

		processor.postProcessBeforeInitialization(routerFunction, "weightRoutes");

		assertThat(events).hasSize(2);
		assertWeightEvent(events.get(0), "weight_high", 8);
		assertWeightEvent(events.get(1), "weight_low", 2);
	}

	private void assertWeightEvent(Object event, String routeId, int weight) {
		assertThat(event).isInstanceOf(AttributedArugmentSuppliedEvent.class);
		AttributedArugmentSuppliedEvent<?> attributed = (AttributedArugmentSuppliedEvent<?>) event;
		assertThat(attributed.getAttributes()).containsEntry(MvcUtils.GATEWAY_ROUTE_ID_ATTR, routeId);
		assertThat(attributed.getArgument()).isInstanceOfSatisfying(WeightConfig.class, config -> {
			assertThat(config.getGroup()).isEqualTo("group1");
			assertThat(config.getWeight()).isEqualTo(weight);
		});
	}

}
