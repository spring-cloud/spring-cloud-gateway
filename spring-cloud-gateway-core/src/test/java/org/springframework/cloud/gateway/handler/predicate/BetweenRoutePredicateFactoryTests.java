/*
 * Copyright 2013-2019 the original author or authors.
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

package org.springframework.cloud.gateway.handler.predicate;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.function.Predicate;

import org.junit.Test;

import org.springframework.boot.convert.ApplicationConversionService;
import org.springframework.cloud.gateway.support.ConfigurationService;
import org.springframework.cloud.gateway.support.StringToZonedDateTimeConverter;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.cloud.gateway.handler.predicate.BetweenRoutePredicateFactory.DATETIME1_KEY;
import static org.springframework.cloud.gateway.handler.predicate.BetweenRoutePredicateFactory.DATETIME2_KEY;

/**
 * @author Spencer Gibb
 */
public class BetweenRoutePredicateFactoryTests {

	static <T> T bindConfig(HashMap<String, Object> properties,
			AbstractRoutePredicateFactory<T> factory) {
		ApplicationConversionService conversionService = new ApplicationConversionService();
		conversionService.addConverter(new StringToZonedDateTimeConverter());
		// @formatter:off
		T config = new ConfigurationService(null, () -> conversionService, () -> null)
				.with(factory)
				.name("myname")
				.normalizedProperties(properties)
				.bind();
		// @formatter:on
		return config;
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
		return ZonedDateTime.now().minusHours(hours)
				.format(DateTimeFormatter.ISO_ZONED_DATE_TIME);
	}

	static String plusHours(int hours) {
		return ZonedDateTime.now().plusHours(hours)
				.format(DateTimeFormatter.ISO_ZONED_DATE_TIME);
	}

	static ServerWebExchange getExchange() {
		MockServerHttpRequest request = MockServerHttpRequest.get("https://example.com")
				.build();
		return MockServerWebExchange.from(request);
	}

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

		ZonedDateTime parse = ZonedDateTime.parse(dateString1);

		final boolean result = runPredicate(dateString1, dateString2);

		assertThat(result).as("Now is not between %s and %s", dateString1, dateString2)
				.isTrue();
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

		assertThat(result).as("Now is not between %s and %s", dateString1, dateString2)
				.isTrue();
	}

	@Test
	public void afterEpochWorks() {
		String dateString1 = minusHoursMillis(2);
		String dateString2 = minusHoursMillis(1);

		final boolean result = runPredicate(dateString1, dateString2);

		assertThat(result).as("Now is not after %s", dateString1).isFalse();
	}

	@Test
	public void testPredicates() {
		boolean result = new BetweenRoutePredicateFactory()
				.apply(c -> c.setDatetime1(ZonedDateTime.now().minusHours(2))
						.setDatetime2(ZonedDateTime.now().plusHours(1)))
				.test(getExchange());
		assertThat(result).isTrue();
	}

	boolean runPredicate(String dateString1, String dateString2) {
		HashMap<String, Object> map = new HashMap<>();
		map.put(DATETIME1_KEY, dateString1);
		map.put(DATETIME2_KEY, dateString2);

		BetweenRoutePredicateFactory factory = new BetweenRoutePredicateFactory();

		BetweenRoutePredicateFactory.Config config = bindConfig(map, factory);

		return factory.apply(config).test(getExchange());
	}

	@Test
	public void toStringFormat() {
		BetweenRoutePredicateFactory.Config config = new BetweenRoutePredicateFactory.Config();
		config.setDatetime1(ZonedDateTime.now());
		config.setDatetime2(ZonedDateTime.now().plusHours(1));
		Predicate predicate = new BetweenRoutePredicateFactory().apply(config);
		assertThat(predicate.toString()).contains(
				"Between: " + config.getDatetime1() + " and " + config.getDatetime2());
	}

}
