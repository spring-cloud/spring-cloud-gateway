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

package org.springframework.cloud.gateway.filter.factory.cache.keygenerator;

import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import org.springframework.http.HttpCookie;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Ignacio Lozano
 */
class DefaultKeyValueGeneratorTests {

	@Test
	void uriAuthorizationAndCookiesArePresent() {
		String uri = "http://myuri";
		HttpHeaders headers = new HttpHeaders();
		String authorization = "my-auth";
		headers.set("Authorization", authorization);
		String cookieName = "my-cookie";
		String cookieValue = "cookie-value";
		HttpCookie cookie = new HttpCookie(cookieName, cookieValue);
		MockServerHttpRequest request = MockServerHttpRequest.get(uri).cookie(cookie).headers(headers).build();

		String result = apply(request);

		assertThat(result).isEqualTo(uri + ";Authorization=" + authorization + ";" + cookieName + "=" + cookieValue);
	}

	@Test
	void uriAndCookiesArePresent() {
		String uri = "http://myuri";
		HttpHeaders headers = new HttpHeaders();
		String cookieName = "my-cookie";
		String cookieValue = "cookie-value";
		HttpCookie cookie = new HttpCookie(cookieName, cookieValue);
		MockServerHttpRequest request = MockServerHttpRequest.get(uri).cookie(cookie).headers(headers).build();

		String result = apply(request);

		assertThat(result).isEqualTo(uri + ";" + "" + ";" + cookieName + "=" + cookieValue);
	}

	@Test
	void onlyUriPresent() {
		String uri = "http://myuri";
		MockServerHttpRequest request = MockServerHttpRequest.get(uri).build();

		String result = apply(request);

		assertThat(result).isEqualTo(uri + ";" + "" + ";" + "");
	}

	public String apply(ServerHttpRequest request) {
		return CacheKeyGenerator.DEFAULT_KEY_VALUE_GENERATORS.stream().map(generator -> generator.apply(request))
				.collect(Collectors.joining(CacheKeyGenerator.KEY_SEPARATOR));
	}

}
