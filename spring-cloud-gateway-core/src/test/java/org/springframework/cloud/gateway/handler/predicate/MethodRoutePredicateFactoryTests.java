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

import java.util.function.Predicate;

import org.junit.Test;
import org.junit.runner.RunWith;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.handler.RoutePredicateHandlerMapping;
import org.springframework.cloud.gateway.handler.predicate.MethodRoutePredicateFactory.Config;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.cloud.gateway.test.BaseWebClientTests;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.junit4.SpringRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = RANDOM_PORT)
@DirtiesContext
public class MethodRoutePredicateFactoryTests extends BaseWebClientTests {

	@Test
	public void methodRouteWorks() {
		testClient.get().uri("/get").header("Host", "www.method.org").exchange()
				.expectStatus().isOk().expectHeader()
				.valueEquals(HANDLER_MAPPER_HEADER,
						RoutePredicateHandlerMapping.class.getSimpleName())
				.expectHeader().valueEquals(ROUTE_ID_HEADER, "method_test_get");
	}

	@Test
	public void methodGetAndPostRouteWorks() {
		testClient.post().uri("/multivalueheaders").header("Host", "www.method.org")
				.exchange().expectStatus().isOk().expectHeader()
				.valueEquals(HANDLER_MAPPER_HEADER,
						RoutePredicateHandlerMapping.class.getSimpleName())
				.expectHeader().valueEquals(ROUTE_ID_HEADER, "method_test_get_and_post");

		testClient.get().uri("/multivalueheaders").header("Host", "www.method.org")
				.exchange().expectStatus().isOk().expectHeader()
				.valueEquals(HANDLER_MAPPER_HEADER,
						RoutePredicateHandlerMapping.class.getSimpleName())
				.expectHeader().valueEquals(ROUTE_ID_HEADER, "method_test_get_and_post");
	}

	@Test
	public void methodRouteNotMatching() {
		testClient.delete().uri("/multivalueheaders").header("Host", "www.method.org")
				.exchange().expectStatus()
				.value(integer -> integer.equals(HttpStatus.METHOD_NOT_ALLOWED))
				.expectHeader()
				.valueEquals(HANDLER_MAPPER_HEADER,
						RoutePredicateHandlerMapping.class.getSimpleName())
				.expectHeader()
				/*
				 * Fallback to route with '/**' path predicate matches, see
				 * application.yml in test resources
				 */
				.valueEquals(ROUTE_ID_HEADER, "default_path_to_httpbin");
	}

	@Test
	public void toStringFormatSingleMethod() {
		Config config = new Config();
		config.setMethods(HttpMethod.GET);
		Predicate predicate = new MethodRoutePredicateFactory().apply(config);
		assertThat(predicate.toString()).contains("Methods: [GET]");
	}

	@Test
	public void toStringFormatMultipleMethods() {
		Config config = new Config();
		config.setMethods(HttpMethod.GET, HttpMethod.PUT);
		Predicate predicate = new MethodRoutePredicateFactory().apply(config);
		assertThat(predicate.toString()).contains("Methods: [GET, PUT]");
	}

	@EnableAutoConfiguration
	@SpringBootConfiguration
	@Import(DefaultTestConfig.class)
	public static class TestConfig {

		@Value("${test.uri}")
		String uri;

		@Bean
		public RouteLocator testRouteLocator(RouteLocatorBuilder builder) {
			return builder.routes()
					.route("method_test_get",
							r -> r.method("GET").and().path("/get")
									.filters(f -> f.prefixPath("/httpbin")).uri(uri))
					.route("method_test_get_and_post",
							r -> r.method("GET", "POST").and().path("/multivalueheaders")
									.filters(f -> f.prefixPath("/httpbin")).uri(uri))
					.build();
		}

	}

}
