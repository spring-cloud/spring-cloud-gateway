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

package org.springframework.cloud.gateway.filter.factory.cache;

import org.junit.jupiter.api.Test;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.http.server.reactive.MockServerHttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Ignacio Lozano
 */
class ResponseCacheGatewayFilterTest {

	ResponseCacheManager cacheManagerToTest = new ResponseCacheManager(null, null, null);

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

}
