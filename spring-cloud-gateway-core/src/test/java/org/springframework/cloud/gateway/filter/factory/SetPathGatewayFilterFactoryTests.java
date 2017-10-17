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

package org.springframework.cloud.gateway.filter.factory;

import java.lang.reflect.Constructor;
import java.net.URI;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;

import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.http.server.reactive.MockServerWebExchange;
import org.springframework.util.ReflectionUtils;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.web.util.pattern.PathPattern.PathMatchInfo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.cloud.gateway.filter.factory.SetPathGatewayFilterFactory.TEMPLATE_KEY;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_ORIGINAL_REQUEST_URL_ATTR;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.URI_TEMPLATE_VARIABLES_ATTRIBUTE;
import static org.springframework.tuple.TupleBuilder.tuple;

import reactor.core.publisher.Mono;

/**
 * @author Spencer Gibb
 */
public class SetPathGatewayFilterFactoryTests {

	@Test
	public void setPathFilterWorks() {
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
		GatewayFilter filter = new SetPathGatewayFilterFactory().apply(tuple().of(TEMPLATE_KEY, template));

		MockServerHttpRequest request = MockServerHttpRequest
				.get("http://localhost"+ actualPath)
				.build();

		ServerWebExchange exchange = new MockServerWebExchange(request);

		try {
			Constructor<PathMatchInfo> constructor = ReflectionUtils.accessibleConstructor(PathMatchInfo.class, Map.class, Map.class);
			constructor.setAccessible(true);
			PathMatchInfo pathMatchInfo = constructor.newInstance(variables, Collections.emptyMap());
			exchange.getAttributes().put(URI_TEMPLATE_VARIABLES_ATTRIBUTE, pathMatchInfo);
		} catch (Exception e) {
			ReflectionUtils.rethrowRuntimeException(e);
		}

		GatewayFilterChain filterChain = mock(GatewayFilterChain.class);

		ArgumentCaptor<ServerWebExchange> captor = ArgumentCaptor.forClass(ServerWebExchange.class);
		when(filterChain.filter(captor.capture())).thenReturn(Mono.empty());

		filter.filter(exchange, filterChain);

		ServerWebExchange webExchange = captor.getValue();

		assertThat(webExchange.getRequest().getURI()).hasPath(expectedPath);
		LinkedHashSet<URI> uris = webExchange.getRequiredAttribute(GATEWAY_ORIGINAL_REQUEST_URL_ATTR);
		assertThat(uris).contains(request.getURI());
	}
}
