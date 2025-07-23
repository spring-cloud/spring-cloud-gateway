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

import java.net.URI;
import java.util.LinkedHashSet;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import reactor.core.publisher.Mono;

import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.factory.StripPrefixGatewayFilterFactory.Config;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_ORIGINAL_REQUEST_URL_ATTR;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_REQUEST_URL_ATTR;

/**
 * @author Ryan Baxter
 */
public class StripPrefixGatewayFilterFactoryTests {

	@Test
	public void testStripPrefix() {
		testStripPrefixFilter("/foo/bar", "/bar", 1);
		testStripPrefixFilter("/foo/bar", "/", 2);
		testStripPrefixFilter("/foo/bar", "/foo/bar", 0);
		testStripPrefixFilter("/foo/bar/", "/", 2);
		testStripPrefixFilter("/foo/bar/", "/foo/bar/", 0);
		testStripPrefixFilter("", "/", 1);
		testStripPrefixFilter("/", "/", 1);
		testStripPrefixFilter("/", "/", 2);
		testStripPrefixFilter("", "/", 2);
		testStripPrefixFilter("/this/is/a/long/path/with/a/lot/of/slashes", "/path/with/a/lot/of/slashes", 4);
	}

	private void testStripPrefixFilter(String actualPath, String expectedPath, int parts) {
		GatewayFilter filter = new StripPrefixGatewayFilterFactory().apply(c -> c.setParts(parts));

		MockServerHttpRequest request = MockServerHttpRequest.get("http://localhost" + actualPath).build();

		ServerWebExchange exchange = MockServerWebExchange.from(request);

		GatewayFilterChain filterChain = mock(GatewayFilterChain.class);

		ArgumentCaptor<ServerWebExchange> captor = ArgumentCaptor.forClass(ServerWebExchange.class);
		when(filterChain.filter(captor.capture())).thenReturn(Mono.empty());

		filter.filter(exchange, filterChain);

		ServerWebExchange webExchange = captor.getValue();

		assertThat(webExchange.getRequest().getURI()).hasPath(expectedPath);

		URI requestUrl = webExchange.getRequiredAttribute(GATEWAY_REQUEST_URL_ATTR);
		assertThat(requestUrl).hasScheme("http").hasHost("localhost").hasNoPort().hasPath(expectedPath);
		LinkedHashSet<URI> uris = webExchange.getRequiredAttribute(GATEWAY_ORIGINAL_REQUEST_URL_ATTR);
		assertThat(uris).contains(request.getURI());
	}

	@Test
	public void toStringFormat() {
		Config config = new Config();
		config.setParts(2);
		GatewayFilter filter = new StripPrefixGatewayFilterFactory().apply(config);
		assertThat(filter.toString()).contains("2");
	}

}
