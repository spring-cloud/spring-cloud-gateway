/*
 * Copyright 2017 the original author or authors.
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

package org.springframework.cloud.gateway.handler.predicate;

import org.junit.Test;
import org.springframework.http.HttpMethod;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.web.server.ServerWebExchange;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.cloud.gateway.handler.predicate.RoutePredicates.host;
import static org.springframework.cloud.gateway.handler.predicate.RoutePredicates.method;
import static org.springframework.cloud.gateway.handler.predicate.RoutePredicates.path;

/**
 * @author Biju Kunjummen
 */
public class RoutePredicatesTest {

	@Test
	public void testHostPredicates() {
		assertThat(host("**").test(mockHostExchange("test.abc.com"))).isTrue();
		assertThat(host("**.abc.**").test(mockHostExchange("test.abc.com"))).isTrue();
		assertThat(host("**.co?").test(mockHostExchange("test.abc.com"))).isTrue();
		assertThat(host("**.co").test(mockHostExchange("test.abc.com"))).isFalse();

	}

	@Test
	public void testPathPredicates() {
		assertThat(path("/**").test(mockPathExchange("/path1/path2"))).isTrue();
		assertThat(path("/path1/*").test(mockPathExchange("/path1/path2"))).isTrue();
		assertThat(path("/path1/path?").test(mockPathExchange("/path1/path2"))).isTrue();
	}

	@Test
	public void testMethodPredicates() {
		assertThat(method("GET").test(mockMethodExchange("GET"))).isTrue();
		assertThat(method("POST").test(mockMethodExchange("POST"))).isTrue();
		assertThat(method("PUT").test(mockMethodExchange("PUT"))).isTrue();
	}

	private ServerWebExchange mockHostExchange(String host) {
		MockServerHttpRequest mockRequest = MockServerHttpRequest.get("/")
				.header("Host", host).build();
		return mockRequest.toExchange();
	}

	private ServerWebExchange mockPathExchange(String path) {
		MockServerHttpRequest mockRequest = MockServerHttpRequest.get(path).build();
		return mockRequest.toExchange();
	}

	private ServerWebExchange mockMethodExchange(String method) {
		MockServerHttpRequest mockRequest = MockServerHttpRequest
				.method(HttpMethod.resolve(method), "/").build();
		return mockRequest.toExchange();
	}
}
