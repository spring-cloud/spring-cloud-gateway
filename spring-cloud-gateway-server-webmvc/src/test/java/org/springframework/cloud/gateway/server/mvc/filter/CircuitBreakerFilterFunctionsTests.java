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

package org.springframework.cloud.gateway.server.mvc.filter;

import java.net.URI;
import java.time.Duration;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.cloud.circuitbreaker.resilience4j.Resilience4JCircuitBreakerFactory;
import org.springframework.cloud.client.circuitbreaker.Customizer;
import org.springframework.cloud.gateway.server.mvc.config.GatewayMvcProperties;
import org.springframework.cloud.gateway.server.mvc.test.HttpbinTestcontainers;
import org.springframework.cloud.gateway.server.mvc.test.LocalServerPortUriResolver;
import org.springframework.cloud.gateway.server.mvc.test.PermitAllSecurityConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerResponse;

import static org.springframework.cloud.gateway.server.mvc.filter.CircuitBreakerFilterFunctions.circuitBreaker;
import static org.springframework.cloud.gateway.server.mvc.filter.FilterFunctions.setPath;
import static org.springframework.cloud.gateway.server.mvc.handler.GatewayRouterFunctions.route;
import static org.springframework.cloud.gateway.server.mvc.handler.HandlerFunctions.http;
import static org.springframework.cloud.gateway.server.mvc.predicate.GatewayRequestPredicates.path;

/**
 * @author raccoonback
 */
@SpringBootTest(properties = { GatewayMvcProperties.PREFIX + ".function.enabled=false" },
		webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ContextConfiguration(initializers = HttpbinTestcontainers.class)
class CircuitBreakerFilterFunctionsTests {

	@LocalServerPort
	int port;

	@Autowired
	RestTestClient restClient;

	@Test
	void circuitBreakerCallNotPermittedExceptionReturns503() {
		restClient.get()
			.uri("/circuitbreaker/forced-open")
			.exchange()
			.expectStatus()
			.isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
	}

	@Test
	void circuitBreakerTimeoutReturns504() {
		restClient.get().uri("/circuitbreaker/timeout").exchange().expectStatus().isEqualTo(HttpStatus.GATEWAY_TIMEOUT);
	}

	@Test
	void circuitBreakerResumeWithoutErrorReturns200() {
		restClient.get().uri("/circuitbreaker/resume-without-error").exchange().expectStatus().isOk();
	}

	@Test
	void circuitBreakerResumeWithoutErrorStillReturns503OnCircuitOpen() {
		restClient.get()
			.uri("/circuitbreaker/resume-without-error-forced-open")
			.exchange()
			.expectStatus()
			.isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
	}

	@Test
	void circuitBreakerResumeWithoutErrorStillReturns504OnTimeout() {
		restClient.get()
			.uri("/circuitbreaker/resume-without-error-timeout")
			.exchange()
			.expectStatus()
			.isEqualTo(HttpStatus.GATEWAY_TIMEOUT);
	}

	@Test
	void circuitBreakerFallbackWorks() {
		restClient.get()
			.uri("/circuitbreaker/with-fallback")
			.exchange()
			.expectStatus()
			.isOk()
			.expectBody(String.class)
			.isEqualTo("fallback response data");
	}

	@SpringBootConfiguration
	@EnableAutoConfiguration
	@Import({ PermitAllSecurityConfiguration.class, LocalServerPortUriResolver.class })
	@RestController
	static class TestConfig {

		@Bean
		public Customizer<Resilience4JCircuitBreakerFactory> circuitBreakerCustomizer() {
			return factory -> {
				factory.addCircuitBreakerCustomizer(CircuitBreaker::transitionToForcedOpenState, "forced-open");

				factory.configure(builder -> builder
					.timeLimiterConfig(TimeLimiterConfig.custom().timeoutDuration(Duration.ofMillis(500)).build())
					.circuitBreakerConfig(CircuitBreakerConfig.ofDefaults()), "timeout");
			};
		}

		@Bean
		public RouterFunction<ServerResponse> circuitBreakerRoutes() {
			return route("circuit_breaker_forced_open").route(path("/circuitbreaker/forced-open"), http())
				.before(new LocalServerPortUriResolver())
				.filter(setPath("/status/200"))
				.filter(circuitBreaker("forced-open"))
				.build()

				.and(route("circuit_breaker_timeout").route(path("/circuitbreaker/timeout"), http())
					.before(new LocalServerPortUriResolver())
					.filter(setPath("/delay/10"))
					.filter(circuitBreaker("timeout"))
					.build())

				.and(route("circuit_breaker_resume_without_error")
					.route(path("/circuitbreaker/resume-without-error"), http())
					.before(new LocalServerPortUriResolver())
					.filter(setPath("/status/500"))
					.filter(circuitBreaker(config -> config.setId("resume-without-error")
						.setResumeWithoutError(true)
						.setStatusCodes("500")))
					.build())

				.and(route("circuit_breaker_resume_without_error_forced_open")
					.route(path("/circuitbreaker/resume-without-error-forced-open"), http())
					.before(new LocalServerPortUriResolver())
					.filter(setPath("/status/200"))
					.filter(circuitBreaker(config -> config.setId("forced-open").setResumeWithoutError(true)))
					.build())

				.and(route("circuit_breaker_resume_without_error_timeout")
					.route(path("/circuitbreaker/resume-without-error-timeout"), http())
					.before(new LocalServerPortUriResolver())
					.filter(setPath("/delay/10"))
					.filter(circuitBreaker(config -> config.setId("timeout").setResumeWithoutError(true)))
					.build())

				.and(route("circuit_breaker_with_fallback").route(path("/circuitbreaker/with-fallback"), http())
					.before(new LocalServerPortUriResolver())
					.filter(setPath("/status/500"))
					.filter(circuitBreaker(config -> config.setId("fallback")
						.setFallbackUri(URI.create("forward:/fallback"))
						.setStatusCodes("500")))
					.build());
		}

		@GetMapping("/delay/{seconds}")
		public String delay(@PathVariable int seconds) throws InterruptedException {
			Thread.sleep(seconds * 1000L);
			return "delayed " + seconds + " seconds";
		}

		@GetMapping("/status/{status}")
		public ResponseEntity<Void> status(@PathVariable int status) {
			return ResponseEntity.status(status).build();
		}

		@GetMapping("/fallback")
		public String fallback() {
			return "fallback response data";
		}

	}

}
