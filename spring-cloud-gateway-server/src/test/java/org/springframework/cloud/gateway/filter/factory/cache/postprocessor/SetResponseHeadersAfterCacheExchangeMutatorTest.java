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

import java.util.List;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.cloud.gateway.filter.factory.cache.CachedResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.http.server.reactive.MockServerHttpResponse;
import org.springframework.mock.web.server.MockServerWebExchange;

/**
 * @author Ignacio Lozano
 */
class SetResponseHeadersAfterCacheExchangeMutatorTest {

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

	@Test
	void headersFromCacheOverrideHeadersFromResponse() {
		SetResponseHeadersAfterCacheExchangeMutator toTest = new SetResponseHeadersAfterCacheExchangeMutator();
		inputExchange.getResponse().getHeaders().set("X-Header-1", "Value-original");
		CachedResponse cachedResponse = new CachedResponse.Builder(HttpStatus.OK).header("X-Header-1", "Value-cached")
				.build();

		toTest.accept(inputExchange, cachedResponse);

		Assertions.assertThat(inputExchange.getResponse().getHeaders()).containsEntry("X-Header-1",
				List.of("Value-cached"));
	}

	@Test
	void headersFromResponseAreDropped() {
		SetResponseHeadersAfterCacheExchangeMutator toTest = new SetResponseHeadersAfterCacheExchangeMutator();
		inputExchange.getResponse().getHeaders().set("X-Header-1", "Value-original");
		CachedResponse cachedResponse = new CachedResponse.Builder(HttpStatus.OK).build();

		toTest.accept(inputExchange, cachedResponse);

		Assertions.assertThat(inputExchange.getResponse().getHeaders()).doesNotContainKey("X-Header-1");
	}

}
