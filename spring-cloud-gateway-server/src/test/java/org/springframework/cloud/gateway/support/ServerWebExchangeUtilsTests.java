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

package org.springframework.cloud.gateway.support;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.assertj.core.api.Assertions;
import org.junit.Test;

import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DefaultDataBuffer;
import org.springframework.http.HttpMethod;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.reactive.function.server.HandlerStrategies;
import org.springframework.web.reactive.function.server.ServerRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.CACHED_REQUEST_BODY_ATTR;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.expand;

public class ServerWebExchangeUtilsTests {

	@Test
	public void expandWorks() {
		HashMap<String, String> vars = new HashMap<>();
		vars.put("foo", "bar");
		vars.put("baz", "bam");

		MockServerWebExchange exchange = mockExchange(vars);

		String expanded = expand(exchange, "my-{foo}-{baz}");
		assertThat(expanded).isEqualTo("my-bar-bam");

		expanded = expand(exchange, "my-noop");
		assertThat(expanded).isEqualTo("my-noop");
	}

	@Test
	public void missingVarThrowsException() {
		MockServerWebExchange exchange = mockExchange(Collections.emptyMap());
		Assertions.assertThatThrownBy(() -> expand(exchange, "my-{foo}-{baz}"))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	public void defaultDataBufferHandling() {
		MockServerWebExchange exchange = mockExchange(Collections.emptyMap());
		exchange.getAttributes().put(CACHED_REQUEST_BODY_ATTR, "foo");

		ServerWebExchangeUtils
				.cacheRequestBodyAndRequest(exchange,
						(serverHttpRequest) -> ServerRequest
								.create(exchange.mutate().request(serverHttpRequest).build(),
										HandlerStrategies.withDefaults().messageReaders())
								.bodyToMono(DefaultDataBuffer.class))
				.block();
	}

	@Test
	public void duplicatedCachingDataBufferHandling() {
		MockServerWebExchange exchange = mockExchange(HttpMethod.POST, Collections.emptyMap());
		DataBuffer dataBufferBeforeCaching = exchange.getResponse().bufferFactory()
				.wrap("Cached buffer".getBytes(StandardCharsets.UTF_8));
		exchange.getAttributes().put(CACHED_REQUEST_BODY_ATTR, dataBufferBeforeCaching);

		ServerWebExchangeUtils
				.cacheRequestBodyAndRequest(exchange,
						(serverHttpRequest) -> ServerRequest
								.create(exchange.mutate().request(serverHttpRequest).build(),
										HandlerStrategies.withDefaults().messageReaders())
								.bodyToMono(DefaultDataBuffer.class))
				.block();

		DataBuffer dataBufferAfterCached = exchange.getAttribute(CACHED_REQUEST_BODY_ATTR);

		Assertions.assertThat(dataBufferBeforeCaching).isEqualTo(dataBufferAfterCached);
	}

	private MockServerWebExchange mockExchange(Map<String, String> vars) {
		return mockExchange(HttpMethod.GET, vars);
	}

	private MockServerWebExchange mockExchange(HttpMethod method, Map<String, String> vars) {

		MockServerHttpRequest request = null;
		if (HttpMethod.GET.equals(method)) {
			request = MockServerHttpRequest.get("/get").build();
		}
		else if (HttpMethod.POST.equals(method)) {
			request = MockServerHttpRequest.post("/post").body("post body");
		}

		Assertions.assertThat(request).as("Method was not one of GET or POST").isNotNull();

		MockServerWebExchange exchange = MockServerWebExchange.from(request);
		ServerWebExchangeUtils.putUriTemplateVariables(exchange, vars);
		return exchange;
	}

}
