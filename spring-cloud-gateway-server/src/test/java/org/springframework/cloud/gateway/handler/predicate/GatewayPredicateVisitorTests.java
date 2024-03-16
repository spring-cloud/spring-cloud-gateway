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

package org.springframework.cloud.gateway.handler.predicate;

import java.util.ArrayList;
import java.util.function.Predicate;

import org.junit.jupiter.api.Test;

import org.springframework.cloud.gateway.handler.AsyncPredicate;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.web.server.ServerWebExchange;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Spencer Gibb
 */
public class GatewayPredicateVisitorTests {

	@Test
	public void asyncPredicateVisitVisitsEachNode() {
		PathRoutePredicateFactory pathRoutePredicateFactory = new PathRoutePredicateFactory();
		HostRoutePredicateFactory hostRoutePredicateFactory = new HostRoutePredicateFactory();
		ReadBodyRoutePredicateFactory readBodyRoutePredicateFactory1 = new ReadBodyRoutePredicateFactory();
		ReadBodyRoutePredicateFactory readBodyRoutePredicateFactory2 = new ReadBodyRoutePredicateFactory();
		AsyncPredicate<ServerWebExchange> predicate = AsyncPredicate
				.from(pathRoutePredicateFactory.apply(pathRoutePredicateFactory.newConfig()))
				.and(AsyncPredicate.from(hostRoutePredicateFactory.apply(hostRoutePredicateFactory.newConfig())))
				.and(readBodyRoutePredicateFactory1.applyAsync(readBodyRoutePredicateFactory1.newConfig()))
				.and(readBodyRoutePredicateFactory2.applyAsync(readBodyRoutePredicateFactory2.newConfig()));

		Route route = Route.async().id("git").uri("http://myuri").asyncPredicate(predicate).build();
		ArrayList<Object> configs = new ArrayList<>();
		route.getPredicate().accept(p -> configs.add(p.getConfig()));

		assertThat(configs).hasSize(4).hasExactlyElementsOfTypes(PathRoutePredicateFactory.Config.class,
				HostRoutePredicateFactory.Config.class, ReadBodyRoutePredicateFactory.Config.class,
				ReadBodyRoutePredicateFactory.Config.class);
	}

	@Test
	public void predicateVisitVisitsEachNode() {
		PathRoutePredicateFactory pathRoutePredicateFactory = new PathRoutePredicateFactory();
		HostRoutePredicateFactory hostRoutePredicateFactory = new HostRoutePredicateFactory();
		Predicate<ServerWebExchange> predicate = pathRoutePredicateFactory.apply(pathRoutePredicateFactory.newConfig())
				.and(hostRoutePredicateFactory.apply(hostRoutePredicateFactory.newConfig()));

		Route route = Route.async().id("git").uri("http://myuri").predicate(predicate).build();
		ArrayList<Object> configs = new ArrayList<>();
		route.getPredicate().accept(p -> configs.add(p.getConfig()));

		assertThat(configs).hasSize(2).hasExactlyElementsOfTypes(PathRoutePredicateFactory.Config.class,
				HostRoutePredicateFactory.Config.class);
	}

}
