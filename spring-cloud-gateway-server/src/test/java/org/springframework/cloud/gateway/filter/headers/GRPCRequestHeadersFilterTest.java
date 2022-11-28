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

package org.springframework.cloud.gateway.filter.headers;

import org.junit.jupiter.api.Test;

import org.springframework.http.HttpHeaders;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Alberto C. Ríos
 */
public class GRPCRequestHeadersFilterTest {

	@Test
	public void shouldIncludeTrailersHeaderIfGRPC() {
		MockServerHttpRequest request = MockServerHttpRequest.get("http://localhost:8080/get")
				.header(HttpHeaders.CONTENT_TYPE, "application/grpc").build();

		GRPCRequestHeadersFilter filter = new GRPCRequestHeadersFilter();

		HttpHeaders headers = filter.filter(request.getHeaders(), MockServerWebExchange.from(request));

		assertThat(headers).containsKeys("te");

		assertThat(headers.getFirst("te")).isEqualTo("trailers");
	}

	@Test
	public void shouldNotIncludeTrailersHeaderIfNotGRPC() {
		MockServerHttpRequest request = MockServerHttpRequest.get("http://localhost:8080/get")
				.header(HttpHeaders.CONTENT_TYPE, "application/json").build();

		GRPCRequestHeadersFilter filter = new GRPCRequestHeadersFilter();

		HttpHeaders headers = filter.filter(request.getHeaders(), MockServerWebExchange.from(request));

		assertThat(headers).doesNotContainKeys("te");
	}

}
