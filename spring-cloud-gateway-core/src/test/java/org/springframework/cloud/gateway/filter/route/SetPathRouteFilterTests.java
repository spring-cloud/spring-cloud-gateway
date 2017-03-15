/*
 * Copyright 2013-2017 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package org.springframework.cloud.gateway.filter.route;

import java.util.HashMap;

import org.assertj.core.api.Assertions;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.http.server.reactive.MockServerHttpResponse;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import org.springframework.web.server.adapter.DefaultServerWebExchange;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import reactor.core.publisher.Mono;

/**
 * @author Spencer Gibb
 */
public class SetPathRouteFilterTests {

	@Test
	public void rewritePathFilterWorks() {
		HashMap<String, String> variables = new HashMap<>();
		testRewriteFilter("/baz/bar", "/foo/bar", "/baz/bar", variables);
	}

	@Test
	public void setPathFilterWithTemplateVarsWorks() {
		HashMap<String, String> variables = new HashMap<>();
		variables.put("id", "123");
		testRewriteFilter("/bar/baz/{id}", "/foo/123", "/bar/baz/123", variables);
	}

	private void testRewriteFilter(String template, String actualPath, String expectedPath, HashMap<String, String> variables) {
		WebFilter filter = new SetPathRouteFilter().apply(new String[]{template});

		MockServerHttpRequest request = MockServerHttpRequest
				.get("http://localhost"+ actualPath)
				.build();

		DefaultServerWebExchange exchange = new DefaultServerWebExchange(request, new MockServerHttpResponse());
		exchange.getAttributes().put(RouterFunctions.URI_TEMPLATE_VARIABLES_ATTRIBUTE, variables);

		WebFilterChain filterChain = mock(WebFilterChain.class);

		ArgumentCaptor<ServerWebExchange> captor = ArgumentCaptor.forClass(ServerWebExchange.class);
		when(filterChain.filter(captor.capture())).thenReturn(Mono.empty());

		filter.filter(exchange, filterChain);

		ServerWebExchange webExchange = captor.getValue();

		Assertions.assertThat(webExchange.getRequest().getURI().getPath()).isEqualTo(expectedPath);
	}
}
