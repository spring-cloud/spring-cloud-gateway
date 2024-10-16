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

import java.util.function.Supplier;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.filter.WeightCalculatorWebFilter;
import org.springframework.cloud.gateway.test.BaseWebClientTests;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

@SpringBootTest(webEnvironment = RANDOM_PORT)
@ActiveProfiles("weights-404")
@DirtiesContext
class WeightRoutePredicateFactoryYaml404Tests extends BaseWebClientTests {

	@Autowired
	private WeightCalculatorWebFilter filter;

	private static Supplier<Double> getRandom(double value) {
		Supplier<Double> random = mock(Supplier.class);
		when(random.get()).thenReturn(value);
		return random;
	}

	@Test
	void weightsFromYamlNot404() {
		filter.setRandomSupplier(getRandom(0.5));

		testClient.get()
			.uri("/get")
			.header(HttpHeaders.HOST, "www.weight4041.org")
			.exchange()
			.expectStatus()
			.isOk()
			.expectHeader()
			.valueEquals(ROUTE_ID_HEADER, "weight_first_404_test_1");
	}

	@EnableAutoConfiguration
	@SpringBootConfiguration
	@Import(DefaultTestConfig.class)
	static class TestConfig {

		TestConfig(WeightCalculatorWebFilter filter) {
			Supplier<Double> random = getRandom(0.4);

			filter.setRandomSupplier(random);
		}

	}

}
