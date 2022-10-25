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

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.cloud.gateway.filter.factory.cache.CachedResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.http.server.reactive.MockServerHttpResponse;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.util.StringUtils;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Ignacio Lozano
 */
class SetMaxAgeHeaderAfterCacheExchangeMutatorTest {

	private static final int SECONDS_LATER = 10;

	private MockServerWebExchange inputExchange;

	private Clock clock;

	private Clock clockSecondsLater;

	@BeforeEach
	void setUp() {
		HttpHeaders responseHeaders = new HttpHeaders();
		responseHeaders.setCacheControl("max-age=1234");
		MockServerHttpRequest httpRequest = MockServerHttpRequest.get("https://this").build();

		inputExchange = MockServerWebExchange.from(httpRequest);
		MockServerHttpResponse httpResponse = inputExchange.getResponse();
		httpResponse.setStatusCode(HttpStatus.OK);
		httpResponse.getHeaders().putAll(responseHeaders);

		clock = Clock.fixed(Instant.now(), Clock.systemDefaultZone().getZone());
		clockSecondsLater = Clock.fixed(clock.instant().plusSeconds(SECONDS_LATER), clock.getZone());
	}

	@Test
	void maxAgeIsNotAdded_whenMaxAgeIsNotPresent() {
		inputExchange.getResponse().getHeaders().setCacheControl((String) null);

		Duration timeToLive = Duration.ofSeconds(30);
		CachedResponse inputCachedResponse = CachedResponse.create(HttpStatus.OK).timestamp(clock.instant()).build();

		SetMaxAgeHeaderAfterCacheExchangeMutator toTest = new SetMaxAgeHeaderAfterCacheExchangeMutator(timeToLive,
				clock);
		toTest.accept(inputExchange, inputCachedResponse);
		assertThat(parseMaxAge(inputExchange.getResponse())).isEmpty();
	}

	@Test
	void maxAgeIsDecreasedByTimePassed_whenFilterIsAppliedAfterSecondsLater() {
		Duration timeToLive = Duration.ofSeconds(30);
		CachedResponse inputCachedResponse = CachedResponse.create(HttpStatus.OK).timestamp(clock.instant()).build();

		SetMaxAgeHeaderAfterCacheExchangeMutator toTest = new SetMaxAgeHeaderAfterCacheExchangeMutator(timeToLive,
				clock);
		toTest.accept(inputExchange, inputCachedResponse);
		Optional<Long> firstMaxAgeSeconds = parseMaxAge(inputExchange.getResponse());

		SetMaxAgeHeaderAfterCacheExchangeMutator toTestSecondsLater = new SetMaxAgeHeaderAfterCacheExchangeMutator(
				timeToLive, clockSecondsLater);
		toTestSecondsLater.accept(inputExchange, inputCachedResponse);
		Optional<Long> secondMaxAgeSeconds = parseMaxAge(inputExchange.getResponse());

		assertThat(firstMaxAgeSeconds).contains(timeToLive.getSeconds());
		assertThat(secondMaxAgeSeconds).contains(timeToLive.getSeconds() - SECONDS_LATER);
	}

	@Test
	void maxAgeIsZero_whenRequestIsCachedMoreThanTimeToLive() {
		Duration timeToLive = Duration.ofSeconds(SECONDS_LATER / 2); // To be staled after
		// SECONDS_LATER
		// passed
		CachedResponse inputCachedResponse = CachedResponse.create(HttpStatus.OK).timestamp(clock.instant()).build();

		SetMaxAgeHeaderAfterCacheExchangeMutator toTest = new SetMaxAgeHeaderAfterCacheExchangeMutator(timeToLive,
				clock);
		toTest.accept(inputExchange, inputCachedResponse);
		Optional<Long> firstMaxAgeSeconds = parseMaxAge(inputExchange.getResponse());

		SetMaxAgeHeaderAfterCacheExchangeMutator toTestSecondsLater = new SetMaxAgeHeaderAfterCacheExchangeMutator(
				timeToLive, clockSecondsLater);
		toTestSecondsLater.accept(inputExchange, inputCachedResponse);
		Optional<Long> secondMaxAgeSeconds = parseMaxAge(inputExchange.getResponse());

		assertThat(firstMaxAgeSeconds).contains(timeToLive.getSeconds());
		assertThat(secondMaxAgeSeconds).contains(0L);
	}

	@Test
	void otherCacheControlValuesAreNotRemoved_whenMaxAgeIsModified() {
		inputExchange.getResponse().getHeaders().setCacheControl("max-stale=12, min-stale=1, max-age=1234");
		Duration timeToLive = Duration.ofSeconds(30);
		CachedResponse inputCachedResponse = CachedResponse.create(HttpStatus.OK).timestamp(clock.instant()).build();

		SetMaxAgeHeaderAfterCacheExchangeMutator toTest = new SetMaxAgeHeaderAfterCacheExchangeMutator(timeToLive,
				clock);
		toTest.accept(inputExchange, inputCachedResponse);

		String[] cacheControlValues = Optional.ofNullable(inputExchange.getResponse().getHeaders().getCacheControl())
				.map(s -> s.split("\\s*,\\s*")).orElse(null);
		assertThat(cacheControlValues).contains("max-stale=12", "min-stale=1");
	}

	@Test
	void otherHeadersAreNotRemoved_whenMaxAgeIsModified() {
		inputExchange.getResponse().getHeaders().put("X-Custom-Header", List.of("DO-NOT-REMOVE"));
		Duration timeToLive = Duration.ofSeconds(30);
		CachedResponse inputCachedResponse = CachedResponse.create(HttpStatus.OK).timestamp(clock.instant()).build();

		SetMaxAgeHeaderAfterCacheExchangeMutator toTest = new SetMaxAgeHeaderAfterCacheExchangeMutator(timeToLive,
				clock);
		toTest.accept(inputExchange, inputCachedResponse);

		List<String> cacheControlValues = inputExchange.getResponse().getHeaders().get("X-Custom-Header");
		assertThat(cacheControlValues).contains("DO-NOT-REMOVE");
	}

	private Optional<Long> parseMaxAge(ServerHttpResponse response) {
		return parseMaxAge(response.getHeaders().getCacheControl());
	}

	private Optional<Long> parseMaxAge(String cacheControlValue) {
		if (StringUtils.hasText(cacheControlValue)) {
			Pattern maxAgePattern = Pattern.compile("\\bmax-age=(\\d+)\\b");
			Matcher matcher = maxAgePattern.matcher(cacheControlValue);
			if (matcher.find()) {
				return Optional.of(Long.parseLong(matcher.group(1)));
			}
		}
		return Optional.empty();
	}

}
