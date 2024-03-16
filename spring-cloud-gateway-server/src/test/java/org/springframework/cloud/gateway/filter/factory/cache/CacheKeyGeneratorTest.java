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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import org.junit.jupiter.api.Test;

import org.springframework.cloud.gateway.filter.factory.cache.keygenerator.CacheKeyGenerator;
import org.springframework.http.HttpCookie;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;

/**
 * @author Ignacio Lozano
 * @author Simone Gerevini
 */
class CacheKeyGeneratorTest {

	final CacheKeyGenerator cacheKeyGenerator = new CacheKeyGenerator();

	@Test
	public void shouldGenerateSameKeyForSameUri() {
		MockServerHttpRequest request1 = MockServerHttpRequest.get("http://this").build();
		MockServerHttpRequest request2 = MockServerHttpRequest.get("http://this").build();

		var key1 = cacheKeyGenerator.generateKey(request1);
		var key2 = cacheKeyGenerator.generateKey(request2);

		assertThat(key1).isEqualTo(key2);
	}

	@Test
	void shouldGenerateDifferentKeyWhenAuthAreDifferent() {
		var uri = "https://this";

		var requestWithoutAuth = MockServerHttpRequest.get(uri).build();
		HttpHeaders httpHeaders = new HttpHeaders();
		httpHeaders.put(AUTHORIZATION, List.of("my-token"));
		var requestWithAuth = MockServerHttpRequest.get(uri).headers(httpHeaders).build();

		var keyWithoutAuth = cacheKeyGenerator.generateKey(requestWithoutAuth);
		var keyWithAuth = cacheKeyGenerator.generateKey(requestWithAuth);

		assertThat(keyWithAuth).isNotEqualTo(keyWithoutAuth);
	}

	@Test
	void shouldGenerateDifferentKeyWhenCookiesAreDifferent() {
		var httpHeaders = new HttpHeaders();
		var uri = "https://this";

		var requestWithoutCookies = MockServerHttpRequest.get(uri).headers(httpHeaders).build();
		var cookies = new HttpCookie[] { new HttpCookie("user", "my-first-cookie") };
		var requestWithCookies = MockServerHttpRequest.get(uri).headers(httpHeaders).cookie(cookies).build();

		var keyWithoutCookies = cacheKeyGenerator.generateKey(requestWithoutCookies);
		var keyWithCookies = cacheKeyGenerator.generateKey(requestWithCookies);

		assertThat(keyWithoutCookies).isNotEqualTo(keyWithCookies);
	}

	@Test
	void shouldGenerateSameKeyWhenSameAuthAndCookieArePresent() {
		var uri = "https://this";
		var cookies = new HttpCookie[] { new HttpCookie("user", "my-first-cookie") };
		var httpHeaders = new HttpHeaders();
		httpHeaders.put(AUTHORIZATION, List.of("my-token"));

		var request1 = MockServerHttpRequest.get(uri).headers(httpHeaders).cookie(cookies).build();
		var request2 = MockServerHttpRequest.get(uri).headers(httpHeaders).cookie(cookies).build();

		var key1 = cacheKeyGenerator.generateKey(request1);
		var key2 = cacheKeyGenerator.generateKey(request2);

		assertThat(key1).isEqualTo(key2);
	}

	@Test
	void shouldGenerateSameKeyWhenVaryHeadersAreEqual() {
		final String varyHeader = "X-MY-VARY";
		var uri = "https://this";

		HttpHeaders httpHeaders = new HttpHeaders();
		httpHeaders.put(varyHeader, List.of("VALUE1"));
		var withFirstVary = MockServerHttpRequest.get(uri).headers(httpHeaders).build();
		HttpHeaders httpHeaders2 = new HttpHeaders();
		httpHeaders2.put(varyHeader, List.of("VALUE1"));
		var withSecondVary = MockServerHttpRequest.get(uri).headers(httpHeaders2).build();

		var keyWithFirstVary = cacheKeyGenerator.generateKey(withFirstVary, varyHeader);
		var keyWithSecondVary = cacheKeyGenerator.generateKey(withSecondVary, varyHeader);

		assertThat(keyWithFirstVary).isEqualTo(keyWithSecondVary);
	}

	@Test
	void shouldGenerateDifferentKeyWhenVaryHeadersAreDifferent() {
		final String varyHeader = "X-MY-VARY";
		var uri = "https://this";

		HttpHeaders httpHeaders = new HttpHeaders();
		httpHeaders.put(varyHeader, List.of("VALUE1"));
		var withFirstVary = MockServerHttpRequest.get(uri).headers(httpHeaders).build();
		HttpHeaders httpHeaders2 = new HttpHeaders();
		httpHeaders.put(varyHeader, List.of("VALUE2"));
		var withSecondVary = MockServerHttpRequest.get(uri).headers(httpHeaders2).build();

		var keyWithFirstVary = cacheKeyGenerator.generateKey(withFirstVary, varyHeader);
		var keyWithSecondVary = cacheKeyGenerator.generateKey(withSecondVary, varyHeader);

		assertThat(keyWithFirstVary).isNotEqualTo(keyWithSecondVary);
	}

	@Test
	void shouldGenerateDifferentKeyWhenVaryHeaderIsMissingInSecondRequest() {
		final String varyHeader = "X-MY-VARY";
		var uri = "https://this";

		HttpHeaders httpHeaders = new HttpHeaders();
		httpHeaders.put(varyHeader, List.of("VALUE1"));
		var withFirstVary = MockServerHttpRequest.get(uri).headers(httpHeaders).build();
		var withSecondVary = MockServerHttpRequest.get(uri).build();

		var keyWithFirstVary = cacheKeyGenerator.generateKey(withFirstVary, varyHeader);
		var keyWithSecondVary = cacheKeyGenerator.generateKey(withSecondVary, varyHeader);

		assertThat(keyWithFirstVary).isNotEqualTo(keyWithSecondVary);
	}

	@Test
	void shouldGenerateDifferentKeyWhenOneOfMultipleVaryHeadersIsDifferent() {
		final String varyHeader = "X-MY-VARY";
		String varyHeader2 = "X-MY-SEC-VARY";
		var uri = "https://this";

		HttpHeaders httpHeaders = new HttpHeaders();
		httpHeaders.put(varyHeader, List.of("VALUE1"));
		httpHeaders.put(varyHeader2, List.of("VALUE1"));
		var withFirstVary = MockServerHttpRequest.get(uri).headers(httpHeaders).build();
		HttpHeaders httpHeaders2 = new HttpHeaders();
		httpHeaders.put(varyHeader, List.of("VALUE2"));
		var withSecondVary = MockServerHttpRequest.get(uri).headers(httpHeaders2).build();

		var keyWithFirstVary = cacheKeyGenerator.generateKey(withFirstVary, varyHeader);
		var keyWithSecondVary = cacheKeyGenerator.generateKey(withSecondVary, varyHeader);

		assertThat(keyWithFirstVary).isNotEqualTo(keyWithSecondVary);
	}

	@Test
	void shouldGenerateDifferentKeyWhenHeadersAreDifferentButValuesAreTheSame() {
		var uri = "https://this";

		HttpHeaders httpHeaders = new HttpHeaders();
		httpHeaders.put("X-MY-VARY-1", List.of("VALUE1"));
		httpHeaders.put("X-MY-VARY-2", List.of("VALUE2"));

		HttpHeaders httpHeaders2 = new HttpHeaders();
		httpHeaders2.put("X-MY-VARY-3", List.of("VALUE1"));
		httpHeaders2.put("X-MY-VARY-4", List.of("VALUE2"));

		var withFirstVary = MockServerHttpRequest.get(uri).headers(httpHeaders).build();
		var withSecondVary = MockServerHttpRequest.get(uri).headers(httpHeaders2).build();

		var keyWithFirstVary = cacheKeyGenerator.generateKey(withFirstVary, "X-MY-VARY-1", "X-MY-VARY-2");
		var keyWithSecondVary = cacheKeyGenerator.generateKey(withSecondVary, "X-MY-VARY-3", "X-MY-VARY-4");

		assertThat(keyWithFirstVary).isNotEqualTo(keyWithSecondVary);
	}

	@Test
	void whenHeaderHasEmptyValue() {
		var uri = "https://this";

		HttpHeaders httpHeaders = new HttpHeaders();
		httpHeaders.put("X-MY-VARY-1", List.of(""));

		var withFirstVary = MockServerHttpRequest.get(uri).headers(httpHeaders).build();
		var withoutVaryHeader = MockServerHttpRequest.get(uri).build();

		var keyWithFirstVary = cacheKeyGenerator.generateKey(withFirstVary, "X-MY-VARY-1");
		var keyWithoutVary = cacheKeyGenerator.generateKey(withFirstVary, "X-MY-VARY-1");

		assertThat(keyWithoutVary).isEqualTo(keyWithFirstVary);
	}

	@Test
	public void shouldNotFailWhenRunningInParallel() throws InterruptedException {
		MockServerHttpRequest request = MockServerHttpRequest.get("http://this").build();
		int numberOfThreads = 100;

		List<Exception> exceptions = executeInParallel(Executors.newFixedThreadPool(numberOfThreads), numberOfThreads,
				() -> cacheKeyGenerator.generateKey(request));

		assertThat(exceptions.size()).isEqualTo(0);
	}

	private List<Exception> executeInParallel(Executor executor, int nThreads, Runnable action)
			throws InterruptedException {
		CountDownLatch ready = new CountDownLatch(nThreads);
		CountDownLatch start = new CountDownLatch(1);
		CountDownLatch done = new CountDownLatch(nThreads);
		List<Exception> exceptions = new ArrayList<>(nThreads);

		for (int i = 0; i < nThreads; i++) {
			executor.execute(() -> {
				ready.countDown();
				try {
					start.await();
					action.run();

				}
				catch (InterruptedException e) {
					Thread.currentThread().interrupt();
				}
				catch (RuntimeException e) {
					exceptions.add(e);
				}
				finally {
					done.countDown();
				}
			});
		}

		ready.await();
		start.countDown();
		done.await();

		return exceptions;
	}

}
