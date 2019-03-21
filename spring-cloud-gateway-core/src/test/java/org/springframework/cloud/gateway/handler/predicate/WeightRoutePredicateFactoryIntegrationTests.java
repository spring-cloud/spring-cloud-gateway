/*
 * Copyright 2013-2018 the original author or authors.
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
 *
 */

package org.springframework.cloud.gateway.handler.predicate;

import java.util.Random;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.filter.WeightCalculatorWebFilter;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.cloud.gateway.test.BaseWebClientTests;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.junit4.SpringRunner;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = RANDOM_PORT)
@DirtiesContext
public class WeightRoutePredicateFactoryIntegrationTests extends BaseWebClientTests {

	@Autowired
	private WeightCalculatorWebFilter filter;

	@Test
	public void highWeight() {
		filter.setRandom(getRandom(0.9));

		testClient.get().uri("/get")
				.header(HttpHeaders.HOST, "www.weighthigh.org")
				.exchange()
				.expectStatus().isOk()
				.expectHeader().valueEquals(ROUTE_ID_HEADER, "weight_high_test");
	}

    @Test
    public void lowWeight() {
		filter.setRandom(getRandom(0.1));

		testClient.get().uri("/get")
				.header(HttpHeaders.HOST, "www.weightlow.org")
				.exchange()
				.expectStatus().isOk()
				.expectHeader().valueEquals(ROUTE_ID_HEADER, "weight_low_test");
	}

	@EnableAutoConfiguration
	@SpringBootConfiguration
	@Import(DefaultTestConfig.class)
	public static class TestConfig {

		@Value("${test.uri}")
		private String uri;

		public TestConfig(WeightCalculatorWebFilter filter) {
			Random random = getRandom(0.4);

			filter.setRandom(random);
		}

		@Bean
		public RouteLocator testRouteLocator(RouteLocatorBuilder builder) {
			return builder.routes()
					.route("weight_low_test", r ->
                            r.weight("group1", 2)
							.and().host("**.weightlow.org")
							.filters(f -> f.prefixPath("/httpbin"))
							.uri(this.uri))
					.build();
		}

	}

	private static Random getRandom(double value) {
		Random random = mock(Random.class);
		when(random.nextDouble())
                .thenReturn(value);
		return random;
	}

}
