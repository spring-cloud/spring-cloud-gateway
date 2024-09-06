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

package org.springframework.cloud.gateway.support.tagsprovider;

import java.util.Collections;
import java.util.List;

import io.micrometer.core.instrument.Tags;
import org.junit.jupiter.api.Test;

import org.springframework.cloud.gateway.handler.predicate.HostRoutePredicateFactory;
import org.springframework.cloud.gateway.handler.predicate.MethodRoutePredicateFactory;
import org.springframework.cloud.gateway.handler.predicate.PathRoutePredicateFactory;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.http.HttpMethod;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_PREDICATE_MATCHED_PATH_ATTR;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_PREDICATE_MATCHED_PATH_ROUTE_ID_ATTR;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR;

/**
 * @author Marta Medio
 * @author Alberto C. Ríos
 */
public class GatewayPathTagsProviderTests {

	private final GatewayPathTagsProvider pathTagsProvider = new GatewayPathTagsProvider();

	private static final String ROUTE_URI = "http://gatewaytagsprovider.org:80";

	@Test
	void addPathToRoutes() {
		List<String> pathList = Collections.singletonList("/git/**");

		PathRoutePredicateFactory.Config pathConfig = new PathRoutePredicateFactory.Config().setPatterns(pathList);
		HostRoutePredicateFactory.Config hostConfig = new HostRoutePredicateFactory.Config()
			.setPatterns(Collections.singletonList("**.myhost.com"));
		Route route = Route.async()
			.id("git")
			.uri(ROUTE_URI)
			.predicate(new PathRoutePredicateFactory().apply(pathConfig)
				.and(new HostRoutePredicateFactory().apply(hostConfig)))
			.build();

		ServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get(ROUTE_URI).build());
		exchange.getAttributes().put(GATEWAY_ROUTE_ATTR, route);
		exchange.getAttributes().put(GATEWAY_PREDICATE_MATCHED_PATH_ATTR, pathList.get(0));
		exchange.getAttributes().put(GATEWAY_PREDICATE_MATCHED_PATH_ROUTE_ID_ATTR, route.getId());

		Tags tags = pathTagsProvider.apply(exchange);
		assertThat(tags.stream().count()).isEqualTo(1);
		assertThat(tags.stream().anyMatch(tag -> "path".equals(tag.getKey()) && tag.getValue().equals(pathList.get(0))))
			.isEqualTo(true);
	}

	@Test
	void addsMultiplePathToRoutes() {
		List<String> pathList = Collections.singletonList("/git/**");
		List<String> pathList2 = Collections.singletonList("/git2/**");

		PathRoutePredicateFactory.Config pathConfig = new PathRoutePredicateFactory.Config().setPatterns(pathList);
		PathRoutePredicateFactory.Config pathConfig2 = new PathRoutePredicateFactory.Config().setPatterns(pathList2);
		Route route = Route.async()
			.id("git")
			.uri(ROUTE_URI)
			.predicate(new PathRoutePredicateFactory().apply(pathConfig)
				.or(new PathRoutePredicateFactory().apply(pathConfig2)))
			.build();

		ServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get(ROUTE_URI).build());
		exchange.getAttributes().put(GATEWAY_ROUTE_ATTR, route);
		exchange.getAttributes().put(GATEWAY_PREDICATE_MATCHED_PATH_ATTR, pathList2.get(0));
		exchange.getAttributes().put(GATEWAY_PREDICATE_MATCHED_PATH_ROUTE_ID_ATTR, route.getId());

		Tags tags = pathTagsProvider.apply(exchange);
		assertThat(tags.stream().count()).isEqualTo(1);
		assertThat(
				tags.stream().anyMatch(tag -> "path".equals(tag.getKey()) && tag.getValue().equals(pathList2.get(0))))
			.isEqualTo(true);
	}

	@Test
	void ignoreRoutesWithoutPath() {
		MethodRoutePredicateFactory.Config config = new MethodRoutePredicateFactory.Config();
		config.setMethods(HttpMethod.GET);
		Route route = Route.async()
			.id("empty")
			.uri(ROUTE_URI)
			.predicate(new MethodRoutePredicateFactory().apply(config))
			.build();

		ServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get(ROUTE_URI).build());
		exchange.getAttributes().put(GATEWAY_ROUTE_ATTR, route);

		Tags tags = pathTagsProvider.apply(exchange);
		assertThat(tags.stream().count()).isEqualTo(0);
	}

}
