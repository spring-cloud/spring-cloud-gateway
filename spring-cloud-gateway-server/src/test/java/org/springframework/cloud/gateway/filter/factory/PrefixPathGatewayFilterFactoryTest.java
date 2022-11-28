/*
 * Copyright 2013-2022 the original author or authors.
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
import java.util.HashMap;
import java.util.LinkedHashSet;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import reactor.core.publisher.Mono;

import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.factory.PrefixPathGatewayFilterFactory.Config;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_ORIGINAL_REQUEST_URL_ATTR;

/**
 * @author Ryan Baxter
 */
public class PrefixPathGatewayFilterFactoryTest {

	@Test
	public void testPrefixPath() {
		testPrefixPathFilter("/foo", "/bar", "/foo/bar");
		testPrefixPathFilter("/foo", "/hello%20world", "/foo/hello%20world");
	}

	@Test
	public void testPrefixPathWithVariable() {
		HashMap<String, String> variables = new HashMap<>();
		variables.put("id", "foo");
		testPrefixPathFilter("/{id}", "/bar", "/foo/bar", variables);
	}

	@Test
	public void testPrefixPathWithMultipleVariables() {
		HashMap<String, String> variables = new HashMap<>();
		variables.put("id", "foo");
		variables.put("hello", "world");
		variables.put("product", "bar");
		testPrefixPathFilter("/{id}/v1/{hello}/{product}", "/test", "/foo/v1/world/bar/test", variables);
	}

	private void testPrefixPathFilter(String prefix, String path, String expectedPath) {
		testPrefixPathFilter(prefix, path, expectedPath, new HashMap<>());
	}

	private void testPrefixPathFilter(String prefix, String path, String expectedPath,
			HashMap<String, String> variables) {
		GatewayFilter filter = new PrefixPathGatewayFilterFactory().apply(c -> c.setPrefix(prefix));
		MockServerHttpRequest request = MockServerHttpRequest.get("http://localhost" + path).build();

		ServerWebExchange exchange = MockServerWebExchange.from(request);
		ServerWebExchangeUtils.putUriTemplateVariables(exchange, variables);

		GatewayFilterChain filterChain = mock(GatewayFilterChain.class);

		ArgumentCaptor<ServerWebExchange> captor = ArgumentCaptor.forClass(ServerWebExchange.class);
		when(filterChain.filter(captor.capture())).thenReturn(Mono.empty());

		filter.filter(exchange, filterChain);

		ServerWebExchange webExchange = captor.getValue();

		assertThat(webExchange.getRequest().getURI()).hasPath(expectedPath);
		LinkedHashSet<URI> uris = webExchange.getRequiredAttribute(GATEWAY_ORIGINAL_REQUEST_URL_ATTR);
		assertThat(uris).contains(request.getURI());
	}

	@Test
	public void toStringFormat() {
		Config config = new Config();
		config.setPrefix("myprefix");
		GatewayFilter filter = new PrefixPathGatewayFilterFactory().apply(config);
		assertThat(filter.toString()).contains("myprefix");
	}

}
