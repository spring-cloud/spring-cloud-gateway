/*
 * Copyright 2013-2023 the original author or authors.
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

package org.springframework.cloud.gateway.server.mvc.test.client;

import java.time.Duration;
import java.util.Objects;
import java.util.function.Consumer;

import org.hamcrest.Matcher;
import org.hamcrest.MatcherAssert;

import org.springframework.http.ResponseCookie;
import org.springframework.test.util.AssertionErrors;

import static org.hamcrest.MatcherAssert.assertThat;

/**
 * Assertions on cookies of the response.
 *
 * @author Rossen Stoyanchev
 * @since 5.3
 */
public class CookieAssertions {

	private final ExchangeResult exchangeResult;

	private final TestRestClient.ResponseSpec responseSpec;

	public CookieAssertions(ExchangeResult exchangeResult, TestRestClient.ResponseSpec responseSpec) {
		this.exchangeResult = exchangeResult;
		this.responseSpec = responseSpec;
	}

	/**
	 * Expect a header with the given name to match the specified values.
	 */
	public TestRestClient.ResponseSpec valueEquals(String name, String value) {
		this.exchangeResult.assertWithDiagnostics(() -> {
			String message = getMessage(name);
			AssertionErrors.assertEquals(message, value, getCookie(name).getValue());
		});
		return this.responseSpec;
	}

	/**
	 * Assert the first value of the response cookie with a Hamcrest {@link Matcher}.
	 */
	public TestRestClient.ResponseSpec value(String name, Matcher<? super String> matcher) {
		String value = getCookie(name).getValue();
		this.exchangeResult.assertWithDiagnostics(() -> {
			String message = getMessage(name);
			MatcherAssert.assertThat(message, value, matcher);
		});
		return this.responseSpec;
	}

	/**
	 * Consume the value of the response cookie.
	 */
	public TestRestClient.ResponseSpec value(String name, Consumer<String> consumer) {
		String value = getCookie(name).getValue();
		this.exchangeResult.assertWithDiagnostics(() -> consumer.accept(value));
		return this.responseSpec;
	}

	/**
	 * Expect that the cookie with the given name is present.
	 */
	public TestRestClient.ResponseSpec exists(String name) {
		getCookie(name);
		return this.responseSpec;
	}

	/**
	 * Expect that the cookie with the given name is not present.
	 */
	public TestRestClient.ResponseSpec doesNotExist(String name) {
		ResponseCookie cookie = this.exchangeResult.getResponseCookies().getFirst(name);
		if (cookie != null) {
			String message = getMessage(name) + " exists with value=[" + cookie.getValue() + "]";
			this.exchangeResult.assertWithDiagnostics(() -> AssertionErrors.fail(message));
		}
		return this.responseSpec;
	}

	/**
	 * Assert a cookie's maxAge attribute.
	 */
	public TestRestClient.ResponseSpec maxAge(String name, Duration expected) {
		Duration maxAge = getCookie(name).getMaxAge();
		this.exchangeResult.assertWithDiagnostics(() -> {
			String message = getMessage(name) + " maxAge";
			AssertionErrors.assertEquals(message, expected, maxAge);
		});
		return this.responseSpec;
	}

	/**
	 * Assert a cookie's maxAge attribute with a Hamcrest {@link Matcher}.
	 */
	public TestRestClient.ResponseSpec maxAge(String name, Matcher<? super Long> matcher) {
		long maxAge = getCookie(name).getMaxAge().getSeconds();
		this.exchangeResult.assertWithDiagnostics(() -> {
			String message = getMessage(name) + " maxAge";
			assertThat(message, maxAge, matcher);
		});
		return this.responseSpec;
	}

	/**
	 * Assert a cookie's path attribute.
	 */
	public TestRestClient.ResponseSpec path(String name, String expected) {
		String path = getCookie(name).getPath();
		this.exchangeResult.assertWithDiagnostics(() -> {
			String message = getMessage(name) + " path";
			AssertionErrors.assertEquals(message, expected, path);
		});
		return this.responseSpec;
	}

	/**
	 * Assert a cookie's path attribute with a Hamcrest {@link Matcher}.
	 */
	public TestRestClient.ResponseSpec path(String name, Matcher<? super String> matcher) {
		String path = getCookie(name).getPath();
		this.exchangeResult.assertWithDiagnostics(() -> {
			String message = getMessage(name) + " path";
			assertThat(message, path, matcher);
		});
		return this.responseSpec;
	}

	/**
	 * Assert a cookie's domain attribute.
	 */
	public TestRestClient.ResponseSpec domain(String name, String expected) {
		String path = getCookie(name).getDomain();
		this.exchangeResult.assertWithDiagnostics(() -> {
			String message = getMessage(name) + " domain";
			AssertionErrors.assertEquals(message, expected, path);
		});
		return this.responseSpec;
	}

	/**
	 * Assert a cookie's domain attribute with a Hamcrest {@link Matcher}.
	 */
	public TestRestClient.ResponseSpec domain(String name, Matcher<? super String> matcher) {
		String domain = getCookie(name).getDomain();
		this.exchangeResult.assertWithDiagnostics(() -> {
			String message = getMessage(name) + " domain";
			assertThat(message, domain, matcher);
		});
		return this.responseSpec;
	}

	/**
	 * Assert a cookie's secure attribute.
	 */
	public TestRestClient.ResponseSpec secure(String name, boolean expected) {
		boolean isSecure = getCookie(name).isSecure();
		this.exchangeResult.assertWithDiagnostics(() -> {
			String message = getMessage(name) + " secure";
			AssertionErrors.assertEquals(message, expected, isSecure);
		});
		return this.responseSpec;
	}

	/**
	 * Assert a cookie's httpOnly attribute.
	 */
	public TestRestClient.ResponseSpec httpOnly(String name, boolean expected) {
		boolean isHttpOnly = getCookie(name).isHttpOnly();
		this.exchangeResult.assertWithDiagnostics(() -> {
			String message = getMessage(name) + " httpOnly";
			AssertionErrors.assertEquals(message, expected, isHttpOnly);
		});
		return this.responseSpec;
	}

	/**
	 * Assert a cookie's sameSite attribute.
	 */
	public TestRestClient.ResponseSpec sameSite(String name, String expected) {
		String sameSite = getCookie(name).getSameSite();
		this.exchangeResult.assertWithDiagnostics(() -> {
			String message = getMessage(name) + " sameSite";
			AssertionErrors.assertEquals(message, expected, sameSite);
		});
		return this.responseSpec;
	}

	private ResponseCookie getCookie(String name) {
		ResponseCookie cookie = this.exchangeResult.getResponseCookies().getFirst(name);
		if (cookie == null) {
			this.exchangeResult.assertWithDiagnostics(() -> AssertionErrors.fail("No cookie with name '" + name + "'"));
		}
		return Objects.requireNonNull(cookie);
	}

	private String getMessage(String cookie) {
		return "Response cookie '" + cookie + "'";
	}

}
