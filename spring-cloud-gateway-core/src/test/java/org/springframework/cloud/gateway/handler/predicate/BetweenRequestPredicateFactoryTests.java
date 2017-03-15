/*
 * Copyright 2013-2017 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package org.springframework.cloud.gateway.handler.predicate;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

import org.junit.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.http.server.reactive.MockServerHttpResponse;
import org.springframework.web.reactive.function.server.PublicDefaultServerRequest;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.server.adapter.DefaultServerWebExchange;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.tuple.TupleBuilder.tuple;

/**
 * @author Spencer Gibb
 */
public class BetweenRequestPredicateFactoryTests {

	@Test
	public void beforeStringWorks() {
		String dateString1 = plusHours(1);
		String dateString2 = plusHours(2);

		final boolean result = runPredicate(dateString1, dateString2);

		assertThat(result).as("Now is not before %s", dateString1).isFalse();
	}

	@Test
	public void betweenStringWorks() {
		String dateString1 = minusHours(1);
		String dateString2 = plusHours(1);

		final boolean result = runPredicate(dateString1, dateString2);

		assertThat(result).as("Now is not between %s and %s", dateString1, dateString2).isTrue();
	}

	@Test
	public void afterStringWorks() {
		String dateString1 = minusHours(2);
		String dateString2 = minusHours(1);

		final boolean result = runPredicate(dateString1, dateString2);

		assertThat(result).as("Now is not after %s", dateString2).isFalse();
	}

	@Test
	public void beforeEpochWorks() {
		String dateString1 = plusHoursMillis(1);
		String dateString2 = plusHoursMillis(2);

		final boolean result = runPredicate(dateString1, dateString2);

		assertThat(result).as("Now is not before %s", dateString1).isFalse();
	}

	@Test
	public void betweenEpochWorks() {
		String dateString1 = minusHoursMillis(1);
		String dateString2 = plusHoursMillis(1);

		final boolean result = runPredicate(dateString1, dateString2);

		assertThat(result).as("Now is not between %s and %s", dateString1, dateString2).isTrue();
	}

	@Test
	public void afterEpochWorks() {
		String dateString1 = minusHoursMillis(2);
		String dateString2 = minusHoursMillis(1);

		final boolean result = runPredicate(dateString1, dateString2);

		assertThat(result).as("Now is not after %s", dateString1).isFalse();
	}

	boolean runPredicate(String dateString1, String dateString2) {
		return new BetweenRequestPredicateFactory().apply(tuple().of("1", dateString1, "2", dateString2)).test(getRequest());
	}

	static String minusHoursMillis(int hours) {
		final int millis = hours * 1000 * 60 * 60;
		return String.valueOf(System.currentTimeMillis() - millis);
	}

	static String plusHoursMillis(int hours) {
		final int millis = hours * 1000 * 60 * 60;
		return String.valueOf(System.currentTimeMillis() + millis);
	}

	static String minusHours(int hours) {
		return ZonedDateTime.now().minusHours(hours).format(DateTimeFormatter.ISO_ZONED_DATE_TIME);
	}

	static String plusHours(int hours) {
		return ZonedDateTime.now().plusHours(hours).format(DateTimeFormatter.ISO_ZONED_DATE_TIME);
	}

	static ServerRequest getRequest() {
		final MockServerHttpRequest request = MockServerHttpRequest.get("http://example.com").build();
		return new PublicDefaultServerRequest(new DefaultServerWebExchange(request, new MockServerHttpResponse()));
	}
}
