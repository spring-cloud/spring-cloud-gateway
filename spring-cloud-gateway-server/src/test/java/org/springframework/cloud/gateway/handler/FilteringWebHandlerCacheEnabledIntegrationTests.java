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

package org.springframework.cloud.gateway.handler;

import java.net.URI;
import java.time.Duration;
import java.util.Arrays;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.filter.FilterDefinition;
import org.springframework.cloud.gateway.handler.predicate.PredicateDefinition;
import org.springframework.cloud.gateway.route.RouteDefinition;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.cloud.gateway.test.BaseWebClientTests;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.web.reactive.function.BodyInserters;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

@SpringBootTest(webEnvironment = RANDOM_PORT,
		properties = { "spring.cloud.gateway.route-filter-cache-enabled=true",
				"management.endpoint.gateway.enabled=true", "management.endpoints.web.exposure.include=*",
				"spring.cloud.gateway.actuator.verbose.enabled=true" })
@DirtiesContext
public class FilteringWebHandlerCacheEnabledIntegrationTests extends BaseWebClientTests {

	@Autowired
	private FilteringWebHandler webHandler;

	@Test
	public void filteringWebHandlerCacheEnabledWorks() {
		// prime the cache
		callRoute("/get");
		assertThat(webHandler.getRouteFilterMap()).hasSize(1);

		callRoute("/anything/testRoute1");

		assertThat(webHandler.getRouteFilterMap()).hasSize(2);

		RouteDefinition testRouteDefinition = new RouteDefinition();
		testRouteDefinition.setId("testRoute2");
		testRouteDefinition.setUri(URI.create("lb://testservice"));

		FilterDefinition filterDefinition = new FilterDefinition("PrefixPath=/httpbin");
		testRouteDefinition.getFilters().add(filterDefinition);

		PredicateDefinition hostRoutePredicateDefinition = new PredicateDefinition("Path=/anything/testRoute2");
		testRouteDefinition.setPredicates(Arrays.asList(hostRoutePredicateDefinition));

		testClient.post()
			.uri("http://localhost:" + port + "/actuator/gateway/routes/testRoute2")
			.accept(MediaType.APPLICATION_JSON)
			.body(BodyInserters.fromValue(testRouteDefinition))
			.exchange()
			.expectStatus()
			.isCreated();

		testClient.post()
			.uri("http://localhost:" + port + "/actuator/gateway/refresh")
			.exchange()
			.expectStatus()
			.isOk();

		callRoute("/get");
		callRoute("/anything/testRoute1");
		callRoute("/anything/testRoute2");

		assertThat(webHandler.getRouteFilterMap()).hasSize(3);
	}

	private void callRoute(String uri) {
		testClient.mutate()
			.responseTimeout(Duration.ofMinutes(5))
			.build()
			.get()
			.uri(uri)
			.exchange()
			.expectStatus()
			.isOk();
	}

	@EnableAutoConfiguration
	@SpringBootConfiguration
	@Import(DefaultTestConfig.class)
	public static class TestConfig {

		@Bean
		RouteLocator testRouteLocator(RouteLocatorBuilder builder) {
			return builder.routes()
				.route("get_route", r -> r.path("/get").filters(f -> f.prefixPath("/httpbin")).uri("lb://testservice"))
				.route("testRoute1",
						r -> r.path("/anything/testRoute1")
							.filters(f -> f.prefixPath("/httpbin"))
							.uri("lb://testservice"))
				.build();
		}

	}

}
