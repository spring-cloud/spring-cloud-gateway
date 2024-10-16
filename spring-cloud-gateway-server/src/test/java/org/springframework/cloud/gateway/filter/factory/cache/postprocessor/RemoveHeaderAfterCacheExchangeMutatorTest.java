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

import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import org.springframework.cloud.gateway.filter.factory.cache.CachedResponse;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.http.server.reactive.MockServerHttpResponse;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpHeaders.CACHE_CONTROL;
import static org.springframework.http.HttpHeaders.CONTENT_TYPE;
import static org.springframework.http.HttpHeaders.EXPIRES;
import static org.springframework.http.HttpHeaders.PRAGMA;

/**
 * @author Abel Salgado Romero
 */
class RemoveHeaderAfterCacheExchangeMutatorTest {

	private static final String HTTP_HEADER_TO_REMOVE = "X-To-Remove";

	@Test
	void onlyHeaderToRemoveFromResponseIsRemoved() {
		final ServerWebExchange inputExchange = setupExchange(Map.of(HTTP_HEADER_TO_REMOVE, "A-Value"));

		final var mutator = new RemoveHeadersAfterCacheExchangeMutator(HTTP_HEADER_TO_REMOVE);
		CachedResponse cachedResponse = new CachedResponse.Builder(HttpStatus.OK).build();

		mutator.accept(inputExchange, cachedResponse);

		assertThat(inputExchange.getResponse().getHeaders()).doesNotContainKey(HTTP_HEADER_TO_REMOVE)
			.containsEntry(CACHE_CONTROL, List.of("max-age=60"))
			.containsEntry(CONTENT_TYPE, List.of("application/octet-stream"))
			.hasSize(2);
	}

	@Test
	void multipleHeadersToRemoveFromResponseAreRemoved() {
		final Map<String, String> headers = Map.of(HTTP_HEADER_TO_REMOVE, "A-Value", PRAGMA, "void", EXPIRES, "0");
		final ServerWebExchange inputExchange = setupExchange(headers);

		final var mutator = new RemoveHeadersAfterCacheExchangeMutator(HTTP_HEADER_TO_REMOVE, PRAGMA, EXPIRES);
		CachedResponse cachedResponse = new CachedResponse.Builder(HttpStatus.OK).build();

		mutator.accept(inputExchange, cachedResponse);

		assertThat(inputExchange.getResponse().getHeaders()).doesNotContainKey(HTTP_HEADER_TO_REMOVE)
			.doesNotContainKey(PRAGMA)
			.doesNotContainKey(EXPIRES)
			.containsEntry(CACHE_CONTROL, List.of("max-age=60"))
			.containsEntry(CONTENT_TYPE, List.of("application/octet-stream"))
			.hasSize(2);
	}

	@Test
	void headersAreNotModifiedIfHeaderToRemoveIsEmpty() {
		final ServerWebExchange inputExchange = setupExchange(Map.of());

		final var mutator = new RemoveHeadersAfterCacheExchangeMutator(HTTP_HEADER_TO_REMOVE);
		CachedResponse cachedResponse = new CachedResponse.Builder(HttpStatus.OK).build();

		mutator.accept(inputExchange, cachedResponse);

		assertThat(inputExchange.getResponse().getHeaders()).containsEntry(CACHE_CONTROL, List.of("max-age=60"))
			.containsEntry(CONTENT_TYPE, List.of("application/octet-stream"))
			.hasSize(2);
	}

	private ServerWebExchange setupExchange(Map<String, String> headersToAdd) {
		HttpHeaders responseHeaders = new HttpHeaders();
		responseHeaders.setCacheControl(CacheControl.maxAge(Duration.ofSeconds(60)));
		responseHeaders.setContentType(MediaType.APPLICATION_OCTET_STREAM);
		headersToAdd.forEach((k, v) -> responseHeaders.set(k, v));

		MockServerHttpRequest httpRequest = MockServerHttpRequest.get("https://this").build();
		MockServerWebExchange inputExchange = MockServerWebExchange.from(httpRequest);
		MockServerHttpResponse httpResponse = inputExchange.getResponse();
		httpResponse.setStatusCode(HttpStatus.OK);
		httpResponse.getHeaders().putAll(responseHeaders);

		return inputExchange;
	}

}
