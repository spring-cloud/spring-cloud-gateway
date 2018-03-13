/*
 * Copyright 2013-2018 the original author or authors.
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

package org.springframework.cloud.gateway.filter.headers;

import java.util.Arrays;
import java.util.List;

import org.junit.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Spencer Gibb
 */
public class HttpHeadersFilterTests {

	@Test
	public void httpHeadersFilterTests() {
		MockServerHttpRequest request = MockServerHttpRequest
				.get("http://localhost:8080/get")
				.header("X-A", "aValue")
				.header("X-B", "bValue")
				.header("X-C", "cValue")
				.build();

		List<HttpHeadersFilter> filters = Arrays.asList(
				r -> HttpHeadersFilterTests.this.filter(r, "X-A"),
                r -> HttpHeadersFilterTests.this.filter(r, "X-B")
		);

		HttpHeaders headers = HttpHeadersFilter.filter(filters, request);

		assertThat(headers).containsOnlyKeys("X-C");
	}

	private HttpHeaders filter(ServerHttpRequest request, String keyToFilter) {
		HttpHeaders original = request.getHeaders();
		HttpHeaders filtered = new HttpHeaders();

		original.entrySet().stream()
                .filter(entry -> !entry.getKey().equals(keyToFilter))
                .forEach(entry -> filtered.addAll(entry.getKey(), entry.getValue()));

		return filtered;
	}
}
