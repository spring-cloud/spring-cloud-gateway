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

package org.springframework.cloud.gateway.filter.factory;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import reactor.core.publisher.Mono;

import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory.NameConfig;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;

import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author Thirunavukkarasu Ravichandran
 */
public class RemoveRequestParameterGatewayFilterFactoryTests {

	private ServerWebExchange exchange;

	private GatewayFilterChain filterChain;

	private ArgumentCaptor<ServerWebExchange> captor;

	@Before
	public void setUp() {
		filterChain = mock(GatewayFilterChain.class);
		captor = ArgumentCaptor.forClass(ServerWebExchange.class);
		when(filterChain.filter(captor.capture())).thenReturn(Mono.empty());

	}

	@Test
	public void removeRequestParameterFilterWorks() {
		MockServerHttpRequest request = MockServerHttpRequest.get("http://localhost")
				.queryParam("foo", singletonList("bar")).build();
		exchange = MockServerWebExchange.from(request);
		NameConfig config = new NameConfig();
		config.setName("foo");
		GatewayFilter filter = new RemoveRequestParameterGatewayFilterFactory().apply(config);

		filter.filter(exchange, filterChain);

		ServerHttpRequest actualRequest = captor.getValue().getRequest();
		assertThat(actualRequest.getQueryParams()).doesNotContainKey("foo");
	}

	@Test
	public void removeRequestParameterFilterWorksWhenParamIsNotPresentInRequest() {
		MockServerHttpRequest request = MockServerHttpRequest.get("http://localhost").build();
		exchange = MockServerWebExchange.from(request);
		NameConfig config = new NameConfig();
		config.setName("foo");
		GatewayFilter filter = new RemoveRequestParameterGatewayFilterFactory().apply(config);

		filter.filter(exchange, filterChain);

		ServerHttpRequest actualRequest = captor.getValue().getRequest();
		assertThat(actualRequest.getQueryParams()).doesNotContainKey("foo");
	}

	@Test
	public void removeRequestParameterFilterShouldOnlyRemoveSpecifiedParam() {
		MockServerHttpRequest request = MockServerHttpRequest.get("http://localhost").queryParam("foo", "bar")
				.queryParam("abc", "xyz").build();
		exchange = MockServerWebExchange.from(request);
		NameConfig config = new NameConfig();
		config.setName("foo");
		GatewayFilter filter = new RemoveRequestParameterGatewayFilterFactory().apply(config);

		filter.filter(exchange, filterChain);

		ServerHttpRequest actualRequest = captor.getValue().getRequest();
		assertThat(actualRequest.getQueryParams()).doesNotContainKey("foo");
		assertThat(actualRequest.getQueryParams()).containsEntry("abc", singletonList("xyz"));
	}

	@Test
	public void removeRequestParameterFilterShouldHandleRemainingParamsWhichRequiringEncoding() {
		MockServerHttpRequest request = MockServerHttpRequest.get("http://localhost").queryParam("foo", "bar")
				.queryParam("aaa", "abc xyz").queryParam("bbb", "[xyz").queryParam("ccc", ",xyz").build();
		exchange = MockServerWebExchange.from(request);
		NameConfig config = new NameConfig();
		config.setName("foo");
		GatewayFilter filter = new RemoveRequestParameterGatewayFilterFactory().apply(config);

		filter.filter(exchange, filterChain);

		ServerHttpRequest actualRequest = captor.getValue().getRequest();
		assertThat(actualRequest.getQueryParams()).doesNotContainKey("foo");
		assertThat(actualRequest.getQueryParams()).containsEntry("aaa", singletonList("abc xyz"));
		assertThat(actualRequest.getQueryParams()).containsEntry("bbb", singletonList("[xyz"));
		assertThat(actualRequest.getQueryParams()).containsEntry("ccc", singletonList(",xyz"));
	}

}
