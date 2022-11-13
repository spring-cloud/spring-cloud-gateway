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
import org.mockito.ArgumentCaptor;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.factory.RewriteRequestHeaderGatewayFilterFactory.Config;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpHeaders.COOKIE;

/**
 * @author aburmeis
 */
public class RewriteRequestHeaderGatewayFilterFactoryUnitTests {

	private RewriteRequestHeaderGatewayFilterFactory filterFactory;

	@Before
	public void setUp() {
		filterFactory = new RewriteRequestHeaderGatewayFilterFactory();
	}

	@Test
	public void testRewriteDollarSlash() {
		Config config = config("FooBar", "/foo/(?<segment>.*)", "/$\\{segment}/$\\{segment}/42");
		ServerWebExchange exchange = requestHeaders("FooBar", "/foo/bar");

		ServerWebExchange chained = verifyGatewayFilter(filterFactory.apply(config), exchange);

		List<String> actualHeaders = chained.getRequest().getHeaders().get("FooBar");
		assertThat(actualHeaders).isNotNull();
		assertThat(actualHeaders).containsExactly("/bar/bar/42");
	}

	@Test
	public void testRewriteMultiple() {
		Config config = config("FooBar", "bar", "cafe");
		ServerWebExchange exchange = requestHeaders("FooBar", "/foo/bar/wat/bar");

		ServerWebExchange chained = verifyGatewayFilter(filterFactory.apply(config), exchange);

		List<String> actualHeaders = chained.getRequest().getHeaders().get("FooBar");
		assertThat(actualHeaders).isNotNull();
		assertThat(actualHeaders).containsExactly("/foo/cafe/wat/cafe");
	}

	@Test
	public void testRewriteMultipleHeaders() {
		Config config = config(COOKIE, "^Test([^=]+)=", "$1=");
		ServerWebExchange exchange = requestHeaders(COOKIE, "TestMyCookie=Value", "TestOtherCookie=Value");

		ServerWebExchange chained = verifyGatewayFilter(filterFactory.apply(config), exchange);

		List<String> actualHeaders = chained.getRequest().getHeaders().get(COOKIE);
		assertThat(actualHeaders).isNotNull();
		assertThat(actualHeaders).containsExactly("MyCookie=Value", "OtherCookie=Value");
	}

	@Test
	public void toStringFormat() {
		Config config = config("myname", "myregexp", "myreplacement");
		GatewayFilter filter = filterFactory.apply(config);
		assertThat(filter.toString()).contains("myname").contains("myregexp").contains("myreplacement");
	}

	private static ServerWebExchange verifyGatewayFilter(GatewayFilter filter, ServerWebExchange exchange) {
		GatewayFilterChain chain = mock(GatewayFilterChain.class);
		ArgumentCaptor<ServerWebExchange> chained = ArgumentCaptor.forClass(ServerWebExchange.class);
		when(chain.filter(any())).thenReturn(Mono.empty());

		StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

		verify(chain).filter(chained.capture());
		return chained.getValue();
	}

	private ServerWebExchange requestHeaders(String name, String... values) {
		MockServerHttpRequest.BaseBuilder<?> builder = MockServerHttpRequest.get("http://localhost/");
		builder.header(name, values);
		return new MockServerWebExchange.Builder(builder.build()).build();
	}

	private Config config(String name, String regexp, String replacement) {
		Config config = filterFactory.newConfig();
		config.setName(name);
		config.setRegexp(regexp);
		config.setReplacement(replacement);
		return config;
	}

}
