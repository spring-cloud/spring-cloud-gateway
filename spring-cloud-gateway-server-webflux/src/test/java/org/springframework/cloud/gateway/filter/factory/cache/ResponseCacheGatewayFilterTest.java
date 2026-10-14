/*
 * Copyright 2013-present the original author or authors.
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

package org.springframework.cloud.gateway.filter.factory.cache;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.http.server.reactive.MockServerHttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Ignacio Lozano
 */
class ResponseCacheGatewayFilterTest {

	ResponseCacheManager cacheManagerToTest = new ResponseCacheManager(null, null, null, null);

	@Test
	void requestShouldBeCacheable() {
		var uri = "http://test.com";
		HttpHeaders httpHeaders = new HttpHeaders();
		httpHeaders.add(HttpHeaders.CACHE_CONTROL, "no-transform");
		var request = MockServerHttpRequest.get(uri).headers(httpHeaders).build();

		assertThat(cacheManagerToTest.isRequestCacheable(request)).isTrue();
	}

	@Test
	void requestShouldNotBeCacheable() {
		var uri = "http://test.com";
		HttpHeaders httpHeaders = new HttpHeaders();
		httpHeaders.add(HttpHeaders.CACHE_CONTROL, "no-store");
		var request = MockServerHttpRequest.get(uri).headers(httpHeaders).build();

		assertThat(cacheManagerToTest.isRequestCacheable(request)).isFalse();

		httpHeaders = new HttpHeaders();
		httpHeaders.add(HttpHeaders.CACHE_CONTROL, "no-transform");
		request = MockServerHttpRequest.post(uri).headers(httpHeaders).build();

		assertThat(cacheManagerToTest.isRequestCacheable(request)).isFalse();
	}

	@Test
	void responseShouldBeCacheable() {
		HttpHeaders headers = new HttpHeaders();
		headers.add(HttpHeaders.CACHE_CONTROL, "public");
		var response = new MockServerHttpResponse();
		response.getHeaders().putAll(headers);
		response.setStatusCode(HttpStatus.OK);

		assertThat(cacheManagerToTest.isResponseCacheable(response)).isTrue();

		headers = new HttpHeaders();
		headers.add(HttpHeaders.CACHE_CONTROL, "public");
		response = new MockServerHttpResponse();
		response.getHeaders().putAll(headers);
		response.setStatusCode(HttpStatus.PARTIAL_CONTENT);

		assertThat(cacheManagerToTest.isResponseCacheable(response)).isTrue();
	}

	@Test
	void responseShouldNotBeCacheable() {
		HttpHeaders headers = new HttpHeaders();
		headers.add(HttpHeaders.CACHE_CONTROL, "public");
		var response = new MockServerHttpResponse();
		response.getHeaders().putAll(headers);
		response.setStatusCode(HttpStatus.TOO_MANY_REQUESTS);

		assertThat(cacheManagerToTest.isResponseCacheable(response)).isFalse();

		headers = new HttpHeaders();
		headers.add(HttpHeaders.CACHE_CONTROL, "private");
		response = new MockServerHttpResponse();
		response.getHeaders().putAll(headers);
		response.setStatusCode(HttpStatus.OK);

		assertThat(cacheManagerToTest.isResponseCacheable(response)).isFalse();

		headers = new HttpHeaders();
		headers.add(HttpHeaders.CACHE_CONTROL, "no-store");
		response = new MockServerHttpResponse();
		response.getHeaders().putAll(headers);
		response.setStatusCode(HttpStatus.OK);

		assertThat(cacheManagerToTest.isResponseCacheable(response)).isFalse();
	}

	@ParameterizedTest
	@ValueSource(strings = { "private, no-store", "no-cache, no-store, max-age=0, must-revalidate",
			"max-age=60, private", "public,private", "PRIVATE", "No-Store", "  max-age=60 ,  no-store  ",
			"private=\"Set-Cookie\"" })
	void responseWithForbiddenDirectiveAmongOthersShouldNotBeCacheable(String cacheControl) {
		var response = new MockServerHttpResponse();
		response.getHeaders().add(HttpHeaders.CACHE_CONTROL, cacheControl);
		response.setStatusCode(HttpStatus.OK);

		assertThat(cacheManagerToTest.isResponseCacheable(response)).isFalse();
	}

	@Test
	void responseWithForbiddenDirectiveInSeparateHeaderLineShouldNotBeCacheable() {
		var response = new MockServerHttpResponse();
		response.getHeaders().add(HttpHeaders.CACHE_CONTROL, "max-age=60");
		response.getHeaders().add(HttpHeaders.CACHE_CONTROL, "private");
		response.setStatusCode(HttpStatus.OK);

		assertThat(cacheManagerToTest.isResponseCacheable(response)).isFalse();
	}

	@ParameterizedTest
	@ValueSource(strings = { "public, max-age=60, example=\"a,no-store,b\"", "example=\"private\"",
			"example=\"a, private\", max-age=60", "example=\"a\\\",no-store\"", "example=\"a\\\\\", max-age=60",
			"example=no-store-extension" })
	void responseWithForbiddenWordOnlyInsideQuotedArgumentShouldBeCacheable(String cacheControl) {
		var response = new MockServerHttpResponse();
		response.getHeaders().add(HttpHeaders.CACHE_CONTROL, cacheControl);
		response.setStatusCode(HttpStatus.OK);

		assertThat(cacheManagerToTest.isResponseCacheable(response)).isTrue();
	}

	@ParameterizedTest
	@ValueSource(strings = { "example=\"a,b\", private", "example=\"a,b\", no-store, max-age=60",
			"example=\"a\\\",b\", no-store", "private, example=\"a,b\"" })
	void responseWithForbiddenDirectiveNextToQuotedArgumentShouldNotBeCacheable(String cacheControl) {
		var response = new MockServerHttpResponse();
		response.getHeaders().add(HttpHeaders.CACHE_CONTROL, cacheControl);
		response.setStatusCode(HttpStatus.OK);

		assertThat(cacheManagerToTest.isResponseCacheable(response)).isFalse();
	}

	@Test
	void responseWithUnterminatedQuotedArgumentShouldNotBeCacheable() {
		var response = new MockServerHttpResponse();
		response.getHeaders().add(HttpHeaders.CACHE_CONTROL, "max-age=60, example=\"a, public");
		response.setStatusCode(HttpStatus.OK);

		assertThat(cacheManagerToTest.isResponseCacheable(response)).isFalse();
	}

	@Test
	void requestWithForbiddenWordOnlyInsideQuotedArgumentShouldBeCacheable() {
		var request = MockServerHttpRequest.get("http://test.com")
			.header(HttpHeaders.CACHE_CONTROL, "max-age=0, example=\"a,no-store,b\"")
			.build();

		assertThat(cacheManagerToTest.isRequestCacheable(request)).isTrue();
	}

	@ParameterizedTest
	@ValueSource(strings = { "public, max-age=60", "no-cache", "max-age=60, no-transform", "s-maxage=60",
			"private-extension" })
	void responseWithoutForbiddenDirectiveShouldBeCacheable(String cacheControl) {
		var response = new MockServerHttpResponse();
		response.getHeaders().add(HttpHeaders.CACHE_CONTROL, cacheControl);
		response.setStatusCode(HttpStatus.OK);

		assertThat(cacheManagerToTest.isResponseCacheable(response)).isTrue();
	}

	@ParameterizedTest
	@ValueSource(strings = { "no-cache, no-store", "max-age=0, no-store", "NO-STORE", "no-transform ,no-store" })
	void requestWithForbiddenDirectiveAmongOthersShouldNotBeCacheable(String cacheControl) {
		var request = MockServerHttpRequest.get("http://test.com")
			.header(HttpHeaders.CACHE_CONTROL, cacheControl)
			.build();

		assertThat(cacheManagerToTest.isRequestCacheable(request)).isFalse();
	}

	@Test
	void requestWithoutForbiddenDirectiveShouldBeCacheable() {
		var request = MockServerHttpRequest.get("http://test.com")
			.header(HttpHeaders.CACHE_CONTROL, "max-age=0, no-cache")
			.build();

		assertThat(cacheManagerToTest.isRequestCacheable(request)).isTrue();
	}

}
