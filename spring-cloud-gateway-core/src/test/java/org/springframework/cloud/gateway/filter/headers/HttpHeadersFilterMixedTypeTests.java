/*
 * Copyright 2018 the original author or authors.
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
 *
 */

package org.springframework.cloud.gateway.filter.headers;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import org.junit.Test;

import org.springframework.cloud.gateway.filter.headers.HttpHeadersFilter.Type;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Biju Kunjummen
 */
public class HttpHeadersFilterMixedTypeTests {

	@Test
	public void relevantDownstreamFiltersShouldActOnHeaders() {
		MockServerHttpRequest mockRequest = MockServerHttpRequest.get("/get")
				.header("header1", "value1").header("header2", "value2")
				.header("header3", "value3").build();

		HttpHeadersFilter filter1 = filterRemovingHeaders(Type.RESPONSE,
				"header1");

		HttpHeadersFilter filter2 = filterRemovingHeaders(Type.REQUEST,
				"header2");

		HttpHeaders result = HttpHeadersFilter.filterRequest(Arrays.asList(filter1, filter2),
				MockServerWebExchange.from(mockRequest));

		assertThat(result).containsOnlyKeys("header1", "header3");
	}
	
	private HttpHeadersFilter filterRemovingHeaders(Type type,
			String... headerNames) {
		Set<String> headerNamesSet = new HashSet<>(Arrays.asList(headerNames));
		HttpHeadersFilter filter = new HttpHeadersFilter() {
			@Override
			public HttpHeaders filter(HttpHeaders headers, ServerWebExchange exchange) {
				HttpHeaders result = new HttpHeaders();
				headers.entrySet().forEach(entry -> {
					if (!headerNamesSet.contains(entry.getKey())) {
						result.put(entry.getKey(), entry.getValue());
					}
				});
				return result;
			}

			@Override
			public boolean supports(Type path) {
				return path.equals(type);
			}
		};
		return filter;
	}
}
