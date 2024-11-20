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

package org.springframework.cloud.gateway.handler.predicate;

import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.Set;
import java.util.function.Predicate;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.Test;

import org.springframework.cloud.gateway.handler.predicate.BeforeRoutePredicateFactory.Config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.cloud.gateway.handler.predicate.BeforeRoutePredicateFactory.DATETIME_KEY;
import static org.springframework.cloud.gateway.handler.predicate.BetweenRoutePredicateFactoryTests.bindConfig;
import static org.springframework.cloud.gateway.handler.predicate.BetweenRoutePredicateFactoryTests.getExchange;
import static org.springframework.cloud.gateway.handler.predicate.BetweenRoutePredicateFactoryTests.minusHours;
import static org.springframework.cloud.gateway.handler.predicate.BetweenRoutePredicateFactoryTests.minusHoursMillis;
import static org.springframework.cloud.gateway.handler.predicate.BetweenRoutePredicateFactoryTests.plusHours;
import static org.springframework.cloud.gateway.handler.predicate.BetweenRoutePredicateFactoryTests.plusHoursMillis;

/**
 * @author Spencer Gibb
 */
public class BeforeRoutePredicateFactoryTests {

	@Test
	public void beforeStringWorks() {
		String dateString = minusHours(1);

		boolean result = runPredicate(dateString);

		assertThat(result).isFalse();
	}

	@Test
	public void afterStringWorks() {
		String dateString = plusHours(1);

		boolean result = runPredicate(dateString);

		assertThat(result).isTrue();
	}

	@Test
	public void beforeEpochWorks() {
		String dateString = minusHoursMillis(1);

		final boolean result = runPredicate(dateString);

		assertThat(result).isFalse();
	}

	@Test
	public void afterEpochWorks() {
		String dateString = plusHoursMillis(1);

		final boolean result = runPredicate(dateString);

		assertThat(result).isTrue();
	}

	@Test
	public void testPredicates() {
		boolean result = new BeforeRoutePredicateFactory().apply(c -> c.setDatetime(ZonedDateTime.now().minusHours(2)))
			.test(getExchange());
		assertThat(result).isFalse();
	}

	private boolean runPredicate(String dateString) {
		HashMap<String, Object> map = new HashMap<>();
		map.put(DATETIME_KEY, dateString);

		BeforeRoutePredicateFactory factory = new BeforeRoutePredicateFactory();

		Config config = bindConfig(map, factory);

		return factory.apply(config).test(getExchange());
	}

	@Test
	public void toStringFormat() {
		Config config = new Config();
		config.setDatetime(ZonedDateTime.now());
		Predicate predicate = new BeforeRoutePredicateFactory().apply(config);
		assertThat(predicate.toString()).contains("Before: " + config.getDatetime());
	}

	@Test
	public void testConfig() {
		try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
			Validator validator = factory.getValidator();

			Config config = new Config();
			config.setDatetime(ZonedDateTime.now());

			assertThat(validator.validate(config).isEmpty()).isTrue();
		}
	}

	@Test
	public void testConfigNullField() {
		try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
			Validator validator = factory.getValidator();

			Config config = new Config();
			Set<ConstraintViolation<Config>> validate = validator.validate(config);

			assertThat(validate.isEmpty()).isFalse();
			assertThat(validate.size()).isEqualTo(1);
		}
	}

}
