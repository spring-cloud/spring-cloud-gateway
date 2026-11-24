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

package org.springframework.cloud.gateway.filter;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.cloud.gateway.event.EnableBodyCachingEvent;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR;

class AdaptCachedBodyGlobalFilterTests {

	private static final String ROUTE_ID = "test-route";

	private static final String OTHER_ROUTE_ID = "other-route";

	private AdaptCachedBodyGlobalFilter filter;

	@BeforeEach
	void setUp() {
		this.filter = new AdaptCachedBodyGlobalFilter();
	}

	@Test
	void shouldEnableBodyCachingForRoute() {
		this.filter.onApplicationEvent(new EnableBodyCachingEvent(this, ROUTE_ID));

		assertThat(isBodyCachingEnabled(route(ROUTE_ID))).isTrue();
	}

	@Test
	void shouldEnableBodyCachingForRouteWithMarkerFilter() {
		Route route = route(ROUTE_ID);
		when(route.getFilters()).thenReturn(
				List.of((CacheRequestBodyGatewayFilter) (exchange, chain) -> reactor.core.publisher.Mono.empty()));

		assertThat(isBodyCachingEnabled(route)).isTrue();
	}

	@Test
	void shouldNotEnableBodyCachingForRouteWithoutMarkerFilter() {
		Route route = route(ROUTE_ID);
		when(route.getFilters()).thenReturn(List.of((exchange, chain) -> reactor.core.publisher.Mono.empty()));

		assertThat(isBodyCachingEnabled(route)).isFalse();
	}

	@Test
	void shouldNotEnableBodyCachingForOtherRoute() {
		this.filter.onApplicationEvent(new EnableBodyCachingEvent(this, ROUTE_ID));

		assertThat(isBodyCachingEnabled(route(OTHER_ROUTE_ID))).isFalse();
	}

	@Test
	void shouldHandleRepeatedEnableEvents() {
		this.filter.onApplicationEvent(new EnableBodyCachingEvent(this, ROUTE_ID));
		this.filter.onApplicationEvent(new EnableBodyCachingEvent(this, ROUTE_ID));

		assertThat(isBodyCachingEnabled(route(ROUTE_ID))).isTrue();
	}

	@Test
	void shouldStopBodyCachingWhenMarkerFilterIsRemoved() {
		Route route = route(ROUTE_ID);

		when(route.getFilters()).thenReturn(
				List.of((CacheRequestBodyGatewayFilter) (exchange, chain) -> reactor.core.publisher.Mono.empty()));

		assertThat(isBodyCachingEnabled(route)).isTrue();

		when(route.getFilters()).thenReturn(List.of());

		assertThat(isBodyCachingEnabled(route)).isFalse();
	}

	private Route route(String routeId) {
		Route route = mock(Route.class);
		when(route.getId()).thenReturn(routeId);
		when(route.getFilters()).thenReturn(List.of());
		return route;
	}

	private boolean isBodyCachingEnabled(Route route) {
		MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.post("/test").body("test"));

		exchange.getAttributes().put(GATEWAY_ROUTE_ATTR, route);

		boolean[] requestWasMutated = { false };

		this.filter.filter(exchange, chainExchange -> {
			requestWasMutated[0] = chainExchange.getRequest() != exchange.getRequest();
			return reactor.core.publisher.Mono.empty();
		}).block();

		return requestWasMutated[0];
	}

}
