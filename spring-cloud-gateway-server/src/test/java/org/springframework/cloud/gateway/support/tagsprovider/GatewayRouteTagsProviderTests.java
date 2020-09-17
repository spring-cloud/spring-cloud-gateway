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

import io.micrometer.core.instrument.Tags;
import org.junit.Test;

import org.springframework.cloud.gateway.route.Route;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR;

/**
 * @author Ingyu Hwang
 */
public class GatewayRouteTagsProviderTests {

	private final GatewayRouteTagsProvider tagsProvider = new GatewayRouteTagsProvider();

	private static final String ROUTE_URI = "http://gatewaytagsprovider.org:80";

	private static final String ROUTE_ID = "test-route";

	private static final Route ROUTE = Route.async().id(ROUTE_ID).uri(ROUTE_URI).predicate(swe -> true).build();

	private static final Tags DEFAULT_TAGS = Tags.of("routeId", ROUTE_ID, "routeUri", ROUTE_URI);

	@Test
	public void routeTags() {
		ServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get(ROUTE_URI).build());
		exchange.getAttributes().put(GATEWAY_ROUTE_ATTR, ROUTE);

		Tags tags = tagsProvider.apply(exchange);
		assertThat(tags).isEqualTo(DEFAULT_TAGS);
	}

	@Test
	public void emptyRoute() {
		ServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get(ROUTE_URI).build());

		Tags tags = tagsProvider.apply(exchange);
		assertThat(tags).isEqualTo(Tags.empty());

	}

}
