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

package org.springframework.cloud.gateway.support.tagsprovider;

import io.micrometer.core.instrument.Tags;
import org.junit.Before;
import org.junit.Test;

import org.springframework.cloud.gateway.route.Route;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpResponseDecorator;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.http.server.reactive.MockServerHttpResponse;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR;

/**
 * @author Ingyu Hwang
 */
public class DefaultGatewayTagsProviderTests {

	private Route route;

	private DefaultGatewayTagsProvider defaultTagsProvider;

	private Tags defaultTags;

	private static final String ROUTE_URI = "http://gatewaytagsprovider.org:80";

	@Before
	public void setup() {
		String routeId = "test-route";
		HttpStatus ok = HttpStatus.OK;

		route = Route.async().id(routeId).uri(ROUTE_URI).predicate(swe -> true).build();

		defaultTagsProvider = new DefaultGatewayTagsProvider();
		defaultTags = Tags.of("outcome", ok.series().name(), "status", ok.name(),
				"httpStatusCode", String.valueOf(ok.value()), "httpMethod", "GET",
				"routeId", routeId, "routeUri", ROUTE_URI);
	}

	@Test
	public void defaultProvider() {
		ServerWebExchange exchange = MockServerWebExchange
				.from(MockServerHttpRequest.get(ROUTE_URI).build());
		exchange.getAttributes().put(GATEWAY_ROUTE_ATTR, route);
		exchange.getResponse().setStatusCode(HttpStatus.OK);

		Tags tags = defaultTagsProvider.apply(exchange);
		assertThat(tags).isEqualTo(defaultTags);
	}

	@Test
	public void statusNotChanged() {
		ServerWebExchange exchange = MockServerWebExchange
				.from(MockServerHttpRequest.get(ROUTE_URI).build());

		Tags tags = defaultTagsProvider.apply(exchange);
		assertThat(tags).isEqualTo(Tags.of("outcome", "CUSTOM", "status", "CUSTOM",
				"httpStatusCode", "NA", "httpMethod", "GET"));
	}

	@Test
	public void notAbstractServerHttpResponse() {
		ServerWebExchange mockExchange = mock(ServerWebExchange.class);
		ServerHttpResponseDecorator responseDecorator = new ServerHttpResponseDecorator(
				new MockServerHttpResponse());
		responseDecorator.setStatusCode(HttpStatus.OK);

		when(mockExchange.getRequest())
				.thenReturn(MockServerHttpRequest.get(ROUTE_URI).build());
		when(mockExchange.getResponse()).thenReturn(responseDecorator);
		when(mockExchange.getAttribute(GATEWAY_ROUTE_ATTR)).thenReturn(route);

		Tags tags = defaultTagsProvider.apply(mockExchange);
		assertThat(tags).isEqualTo(defaultTags);
	}

}
