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

package org.springframework.cloud.gateway.filter.factory;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.WebSessionStickyLoadBalancerFilter;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link WebSessionStickyGatewayFilterFactory}.
 *
 * @author Beteab Gebru
 */
class WebSessionStickyGatewayFilterFactoryTests {

	private WebSessionStickyGatewayFilterFactory factory;

	private GatewayFilterChain chain;

	private ServerWebExchange exchange;

	@BeforeEach
	void setUp() {
		factory = new WebSessionStickyGatewayFilterFactory();
		chain = mock(GatewayFilterChain.class);
		exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/test").build());
		when(chain.filter(any())).thenReturn(Mono.empty());
	}

	@Test
	void shouldSetIsStickyAttributeToTrue() {
		GatewayFilter filter = factory.apply((Object) null);

		filter.filter(exchange, chain).block();

		assertThat((Boolean) exchange.getAttribute(WebSessionStickyLoadBalancerFilter.IS_STICKY_ATTRIBUTE)).isTrue();
	}

	@Test
	void shouldContinueFilterChain() {
		GatewayFilter filter = factory.apply((Object) null);

		filter.filter(exchange, chain).block();

		verify(chain).filter(exchange);
	}

	@Test
	void shouldNotSetAttributeWhenNotApplied() {
		Object attribute = exchange.getAttribute(WebSessionStickyLoadBalancerFilter.IS_STICKY_ATTRIBUTE);
		assertThat(attribute).isNull();
	}

	@Test
	void toStringShouldIncludeFilterName() {
		GatewayFilter filter = factory.apply((Object) null);

		String description = filter.toString();
		assertThat(description).contains("WebSessionSticky");
	}

}
