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
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.assertj.core.api.Assertions;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.http.server.reactive.MockServerWebExchange;
import org.springframework.util.ReflectionUtils;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import org.springframework.web.util.pattern.PathPattern;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.cloud.gateway.filter.factory.SetPathWebFilterFactory.TEMPLATE_KEY;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.URI_TEMPLATE_VARIABLES_ATTRIBUTE;
import static org.springframework.tuple.TupleBuilder.tuple;

import reactor.core.publisher.Mono;

/**
 * @author Spencer Gibb
 */
public class SetPathWebFilterFactoryTests {

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
		WebFilter filter = new SetPathWebFilterFactory().apply(tuple().of(TEMPLATE_KEY, template));

		MockServerHttpRequest request = MockServerHttpRequest
				.get("http://localhost"+ actualPath)
				.build();

		ServerWebExchange exchange = new MockServerWebExchange(request);

		try {
			Constructor<PathPattern.PathMatchResult> constructor = ReflectionUtils.accessibleConstructor(PathPattern.PathMatchResult.class, Map.class, Map.class);
			constructor.setAccessible(true);
			PathPattern.PathMatchResult pathMatchResult = constructor.newInstance(variables, Collections.emptyMap());
			exchange.getAttributes().put(URI_TEMPLATE_VARIABLES_ATTRIBUTE, pathMatchResult);
		} catch (Exception e) {
			ReflectionUtils.rethrowRuntimeException(e);
		}

		WebFilterChain filterChain = mock(WebFilterChain.class);

		ArgumentCaptor<ServerWebExchange> captor = ArgumentCaptor.forClass(ServerWebExchange.class);
		when(filterChain.filter(captor.capture())).thenReturn(Mono.empty());

		filter.filter(exchange, filterChain);

		ServerWebExchange webExchange = captor.getValue();

		Assertions.assertThat(webExchange.getRequest().getURI().getPath()).isEqualTo(expectedPath);
	}
}
