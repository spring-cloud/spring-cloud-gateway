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

import java.util.List;

import org.junit.Before;
import org.junit.Test;

import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.RewriteResponseHeaderGatewayFilterFactory.Config;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.server.ServerWebExchange;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpHeaders.SET_COOKIE;

public class RewriteResponseHeaderGatewayFilterFactoryUnitTests {

	private RewriteResponseHeaderGatewayFilterFactory filterFactory;

	@Before
	public void setUp() {
		filterFactory = new RewriteResponseHeaderGatewayFilterFactory();
	}

	@Test
	public void testRewriteDollarSlash() {
		assertThat(filterFactory.rewrite("/foo/bar", "/foo/(?<segment>.*)", "/$\\{segment}/$\\{segment}/42"))
				.isEqualTo("/bar/bar/42");
	}

	@Test
	public void testRewriteMultiple() {
		assertThat(filterFactory.rewrite("/foo/bar/wat/bar", "bar", "cafe")).isEqualTo("/foo/cafe/wat/cafe");
	}

	@Test
	public void testRewriteMultipleHeaders() {
		Config config = new Config();
		config.setName(SET_COOKIE);
		config.setRegexp("SameSite[^;]+");
		config.setReplacement("SameSite=Strict");

		ServerWebExchange exchange = mock(ServerWebExchange.class);
		ServerHttpResponse response = mock(ServerHttpResponse.class);
		HttpHeaders headers = new HttpHeaders();
		headers.add(SET_COOKIE, "TestCookie=Value;SameSite=Lax");
		headers.add(SET_COOKIE, "OtherCookie=Value;SameSite=Lax");
		when(response.getHeaders()).thenReturn(headers);
		when(exchange.getResponse()).thenReturn(response);

		filterFactory.rewriteHeaders(exchange, config);
		List<String> actualHeaders = headers.get(SET_COOKIE);
		assertThat(actualHeaders).isNotNull();
		assertThat(actualHeaders).containsExactly("TestCookie=Value;SameSite=Strict",
				"OtherCookie=Value;SameSite=Strict");
	}

	@Test
	public void toStringFormat() {
		Config config = new Config();
		config.setName("myname");
		config.setRegexp("myregexp");
		config.setReplacement("myreplacement");
		GatewayFilter filter = new RewriteResponseHeaderGatewayFilterFactory().apply(config);
		assertThat(filter.toString()).contains("myname").contains("myregexp").contains("myreplacement");
	}

}
