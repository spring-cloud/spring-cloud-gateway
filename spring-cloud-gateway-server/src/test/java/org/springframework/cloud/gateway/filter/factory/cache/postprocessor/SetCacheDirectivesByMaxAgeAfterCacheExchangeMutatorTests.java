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

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.http.server.reactive.MockServerHttpResponse;
import org.springframework.mock.web.server.MockServerWebExchange;

import static org.assertj.core.api.Assertions.assertThat;

class SetCacheDirectivesByMaxAgeAfterCacheExchangeMutatorTests {

	private MockServerWebExchange inputExchange;

	private SetCacheDirectivesByMaxAgeAfterCacheExchangeMutator toTest;

	@BeforeEach
	void setUp() {
		HttpHeaders responseHeaders = new HttpHeaders();
		responseHeaders.setCacheControl("max-age=1234");
		MockServerHttpRequest httpRequest = MockServerHttpRequest.get("https://this").build();

		inputExchange = MockServerWebExchange.from(httpRequest);
		MockServerHttpResponse httpResponse = inputExchange.getResponse();
		httpResponse.setStatusCode(HttpStatus.OK);
		httpResponse.getHeaders().putAll(responseHeaders);

		toTest = new SetCacheDirectivesByMaxAgeAfterCacheExchangeMutator();
	}

	@ParameterizedTest
	@ValueSource(strings = { "", "ETag=1234-123", "s-max-age=20" })
	void doesntModifyCacheControlWhenNoMaxAge(String cacheControlValue) {
		inputExchange.getResponse().getHeaders().setCacheControl(cacheControlValue);

		toTest.accept(inputExchange, null);

		assertThat(inputExchange.getResponse().getHeaders().getCacheControl()).isEqualTo(cacheControlValue);
	}

	@ParameterizedTest
	@ValueSource(
			strings = { "max-age=0", "ETag=1234-123,max-age=0", "s-max-age=20,max-age=0", "ETag=with-spaces, max-age=0",
					"ETag=with-spaces, max-age=0,Expires=123123123", " max-age=0, ETag=with-spaces" })
	void directivesNoCacheAreAddedWhenMaxAgeIsZero(String cacheControlValue) {
		inputExchange.getResponse().getHeaders().setCacheControl(cacheControlValue);

		toTest.accept(inputExchange, null);

		assertThat(inputExchange.getResponse().getHeaders().getCacheControl()).doesNotContainPattern(",\\s*,")
			.contains("max-age=0")
			.contains("must-revalidate")
			.contains("no-cache");
	}

	@ParameterizedTest
	@ValueSource(strings = { "max-age=10,must-revalidate", "must-revalidate,ETag=1234-123,max-age=10",
			"must-revalidate,s-max-age=0,max-age=10", " max-age=10, must-revalidate,ETag=with-spaces",
			"ETag=with-spaces,must-revalidate, max-age=10,Expires=123123123",
			"ETag=with-spaces,must-revalidate, max-age=10", "max-age=10,no-store" })
	void directivesNoCacheAreRemovedWhenMaxAgePositive(String cacheControlValue) {
		inputExchange.getResponse().getHeaders().setCacheControl(cacheControlValue);

		toTest.accept(inputExchange, null);

		assertThat(inputExchange.getResponse().getHeaders().getCacheControl()).contains("max-age=10")
			.doesNotContainPattern(",\\s*,")
			.doesNotContain("no-store")
			.doesNotContain("must-revalidate")
			.doesNotContain("no-cache");
	}

}
