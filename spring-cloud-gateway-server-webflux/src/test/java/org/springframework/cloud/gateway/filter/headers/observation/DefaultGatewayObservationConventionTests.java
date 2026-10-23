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

package org.springframework.cloud.gateway.filter.headers.observation;

import io.micrometer.common.KeyValue;
import io.micrometer.common.KeyValues;
import org.junit.jupiter.api.Test;

import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultGatewayObservationConventionTests {

	@Test
	void getName_returnsSpringCloudGatewayPrefixedName() {
		assertThat(DefaultGatewayObservationConvention.INSTANCE.getName())
			.isEqualTo("spring.cloud.gateway.http.client.requests");
	}

	@Test
	void getName_doesNotCollideWithSpringWebClientMetric() {
		assertThat(DefaultGatewayObservationConvention.INSTANCE.getName()).isNotEqualTo("http.client.requests");
	}

	@Test
	void getContextualName_returnsHttpMethodPrefixedName() {
		MockServerHttpRequest request = MockServerHttpRequest.get("http://localhost:8080/foo").build();
		MockServerWebExchange exchange = MockServerWebExchange.from(request);
		GatewayContext context = new GatewayContext(new HttpHeaders(), exchange.getRequest(), exchange);

		assertThat(DefaultGatewayObservationConvention.INSTANCE.getContextualName(context)).isEqualTo("HTTP GET");
	}

	@Test
	void getLowCardinalityKeyValues_pinsGatewayRouteKeys() {
		MockServerHttpRequest request = MockServerHttpRequest.get("http://localhost:8080/foo").build();
		Route route = Route.async()
			.id("test-route")
			.uri("http://localhost:8080/")
			.order(1)
			.predicate(exchange -> true)
			.build();
		MockServerWebExchange exchange = MockServerWebExchange.from(request);
		exchange.getAttributes().put(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR, route);
		GatewayContext context = new GatewayContext(new HttpHeaders(), exchange.getRequest(), exchange);

		KeyValues keyValues = DefaultGatewayObservationConvention.INSTANCE.getLowCardinalityKeyValues(context);

		assertThat(keyValues.stream().map(KeyValue::getKey)).containsExactlyInAnyOrder("http.method",
				"http.status_code", "spring.cloud.gateway.route.id", "spring.cloud.gateway.route.uri");
	}

}
