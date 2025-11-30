/*
 * Copyright 2025-present the original author or authors.
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

package org.springframework.cloud.gateway.server.mvc.predicate;

import java.time.ZonedDateTime;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.cloud.gateway.server.mvc.test.HttpbinTestcontainers;
import org.springframework.cloud.gateway.server.mvc.test.HttpbinUriResolver;
import org.springframework.cloud.gateway.server.mvc.test.PermitAllSecurityConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerResponse;

import static org.springframework.cloud.gateway.server.mvc.filter.AfterFilterFunctions.addResponseHeader;
import static org.springframework.cloud.gateway.server.mvc.filter.FilterFunctions.setPath;
import static org.springframework.cloud.gateway.server.mvc.handler.GatewayRouterFunctions.route;
import static org.springframework.cloud.gateway.server.mvc.handler.HandlerFunctions.http;
import static org.springframework.cloud.gateway.server.mvc.predicate.GatewayRequestPredicates.after;
import static org.springframework.cloud.gateway.server.mvc.predicate.GatewayRequestPredicates.before;
import static org.springframework.cloud.gateway.server.mvc.predicate.GatewayRequestPredicates.between;

/**
 * Integration tests for datetime predicates (After, Before, Between) with YAML
 * configuration.
 *
 * @author raccoonback
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT,
		properties = { "spring.cloud.gateway.server.webmvc.function.enabled=false" })
@ActiveProfiles("datetime")
@ContextConfiguration(initializers = HttpbinTestcontainers.class)
class DateTimePredicateIntegrationTests {

	@Autowired
	RestTestClient testClient;

	@Test
	void afterPredicateWorksWithYamlConfig() {
		testClient.get()
			.uri("/test/after")
			.exchange()
			.expectStatus()
			.isOk()
			.expectHeader()
			.valueEquals("X-Predicate-Type", "After");
	}

	@Test
	void beforePredicateWorksWithYamlConfig() {
		testClient.get()
			.uri("/test/before")
			.exchange()
			.expectStatus()
			.isOk()
			.expectHeader()
			.valueEquals("X-Predicate-Type", "Before");
	}

	@Test
	void betweenPredicateWorksWithYamlConfig() {
		testClient.get()
			.uri("/test/between")
			.exchange()
			.expectStatus()
			.isOk()
			.expectHeader()
			.valueEquals("X-Predicate-Type", "Between");
	}

	@Test
	void betweenPredicateWorksWithEpochMilliseconds() {
		testClient.get()
			.uri("/test/between-epoch")
			.exchange()
			.expectStatus()
			.isOk()
			.expectHeader()
			.valueEquals("X-Predicate-Type", "BetweenEpoch");
	}

	@Test
	void afterPredicateRejectsPastTime() {
		// This route requires future time, should NOT match
		testClient.get().uri("/test/future-only").exchange().expectStatus().isNotFound();
	}

	@Test
	void beforePredicateRejectsFutureTime() {
		// This route requires time before 2020, should NOT match
		testClient.get().uri("/test/past-only").exchange().expectStatus().isNotFound();
	}

	@Test
	void betweenPredicateRejectsOutsideRange() {
		// This route requires time in 2020, should NOT match
		testClient.get().uri("/test/outside-range").exchange().expectStatus().isNotFound();
	}

	@Test
	void betweenPredicateRejectsFutureRange() {
		// This route requires time in 2099, should NOT match
		testClient.get().uri("/test/future-range").exchange().expectStatus().isNotFound();
	}

	@Test
	void afterPredicateWorksWithJavaDsl() {
		testClient.get()
			.uri("/test/after-dsl")
			.exchange()
			.expectStatus()
			.isOk()
			.expectHeader()
			.valueEquals("X-Predicate-Type", "AfterDSL");
	}

	@Test
	void beforePredicateWorksWithJavaDsl() {
		testClient.get()
			.uri("/test/before-dsl")
			.exchange()
			.expectStatus()
			.isOk()
			.expectHeader()
			.valueEquals("X-Predicate-Type", "BeforeDSL");
	}

	@Test
	void betweenPredicateWorksWithJavaDsl() {
		testClient.get()
			.uri("/test/between-dsl")
			.exchange()
			.expectStatus()
			.isOk()
			.expectHeader()
			.valueEquals("X-Predicate-Type", "BetweenDSL");
	}

	@Test
	void afterPredicateRejectsPastTimeWithJavaDsl() {
		// This route requires future time, should NOT match
		testClient.get().uri("/test/after-future-dsl").exchange().expectStatus().isNotFound();
	}

	@Test
	void beforePredicateRejectsFutureTimeWithJavaDsl() {
		// This route requires past time, should NOT match
		testClient.get().uri("/test/before-past-dsl").exchange().expectStatus().isNotFound();
	}

	@Test
	void betweenPredicateRejectsOutsideRangeWithJavaDsl() {
		// This route requires time outside current range, should NOT match
		testClient.get().uri("/test/between-past-dsl").exchange().expectStatus().isNotFound();
	}

	@EnableAutoConfiguration
	@SpringBootConfiguration
	@Import(PermitAllSecurityConfiguration.class)
	static class TestConfig {

		@Bean
		public RouterFunction<ServerResponse> dateTimeRoutes() {
			// @formatter:off
			// Success cases
			return route("after_dsl")
					.GET("/test/after-dsl", after(ZonedDateTime.now().minusDays(1)), http())
					.filter(setPath("/anything/after-dsl"))
					.before(new HttpbinUriResolver())
					.after(addResponseHeader("X-Predicate-Type", "AfterDSL"))
					.build()
				.and(route("before_dsl")
					.GET("/test/before-dsl", before(ZonedDateTime.now().plusDays(1)), http())
					.filter(setPath("/anything/before-dsl"))
					.before(new HttpbinUriResolver())
					.after(addResponseHeader("X-Predicate-Type", "BeforeDSL"))
					.build())
				.and(route("between_dsl")
					.GET("/test/between-dsl", between(ZonedDateTime.now().minusDays(1), ZonedDateTime.now().plusDays(1)), http())
					.filter(setPath("/anything/between-dsl"))
					.before(new HttpbinUriResolver())
					.after(addResponseHeader("X-Predicate-Type", "BetweenDSL"))
					.build())
				// Failure cases - should NOT match
				.and(route("after_future_dsl")
					.GET("/test/after-future-dsl", after(ZonedDateTime.now().plusDays(365)), http())
					.filter(setPath("/anything/after-future-dsl"))
					.before(new HttpbinUriResolver())
					.after(addResponseHeader("X-Predicate-Type", "AfterFutureDSL"))
					.build())
				.and(route("before_past_dsl")
					.GET("/test/before-past-dsl", before(ZonedDateTime.now().minusDays(365)), http())
					.filter(setPath("/anything/before-past-dsl"))
					.before(new HttpbinUriResolver())
					.after(addResponseHeader("X-Predicate-Type", "BeforePastDSL"))
					.build())
				.and(route("between_past_dsl")
					.GET("/test/between-past-dsl", between(ZonedDateTime.now().minusDays(730), ZonedDateTime.now().minusDays(365)), http())
					.filter(setPath("/anything/between-past-dsl"))
					.before(new HttpbinUriResolver())
					.after(addResponseHeader("X-Predicate-Type", "BetweenPastDSL"))
					.build());
			// @formatter:on
		}

	}

}
