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

package org.springframework.cloud.gateway.filter.factory.cache.postprocessor;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import org.springframework.cloud.gateway.filter.factory.cache.CachedResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.http.server.reactive.MockServerHttpResponse;
import org.springframework.mock.web.server.MockServerWebExchange;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Ignacio Lozano
 */
class SetStatusCodeAfterCacheExchangeMutatorTest {

	private MockServerWebExchange inputExchange;

	@BeforeEach
	void setUp() {
		HttpHeaders responseHeaders = new HttpHeaders();
		responseHeaders.setCacheControl("max-age=1234");
		MockServerHttpRequest httpRequest = MockServerHttpRequest.get("https://this").build();

		inputExchange = MockServerWebExchange.from(httpRequest);
		MockServerHttpResponse httpResponse = inputExchange.getResponse();
		httpResponse.setStatusCode(HttpStatus.OK);
		httpResponse.getHeaders().putAll(responseHeaders);
	}

	@ParameterizedTest
	@ValueSource(ints = { 200, 400, 404, 500 })
	void statusCodeIsSetFromCachedResponse(int statusCode) {
		CachedResponse cachedResponse = CachedResponse.create(HttpStatus.valueOf(statusCode)).body("some-data").build();
		MockServerHttpRequest httpRequest = MockServerHttpRequest.get("https://this").build();

		inputExchange = MockServerWebExchange.from(httpRequest);

		SetStatusCodeAfterCacheExchangeMutator toTest = new SetStatusCodeAfterCacheExchangeMutator();
		toTest.accept(inputExchange, cachedResponse);

		assertThat(inputExchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.valueOf(statusCode));
	}

}
