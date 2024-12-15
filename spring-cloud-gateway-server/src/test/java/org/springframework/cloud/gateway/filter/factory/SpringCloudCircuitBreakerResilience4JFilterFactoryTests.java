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

package org.springframework.cloud.gateway.filter.factory;

import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.timelimiter.TimeLimiterRegistry;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.circuitbreaker.resilience4j.ReactiveResilience4JCircuitBreakerFactory;
import org.springframework.cloud.circuitbreaker.resilience4j.Resilience4JCircuitBreakerFactory;
import org.springframework.cloud.client.circuitbreaker.Customizer;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.test.BaseWebClientTests;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.BodyInserters;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.core.StringContains.containsString;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

/**
 * @author Ryan Baxter
 */
@SpringBootTest(webEnvironment = RANDOM_PORT,
		properties = { "logging.level.org.springframework.cloud.gateway=INFO", "debug=true",
				"spring.cloud.circuitbreaker.hystrix.enabled=false" })
@ContextConfiguration(classes = SpringCloudCircuitBreakerResilience4JFilterFactoryTests.Config.class)
@DirtiesContext
public class SpringCloudCircuitBreakerResilience4JFilterFactoryTests
		extends SpringCloudCircuitBreakerFilterFactoryTests {

	private static final String RETRIEVED_EXCEPTION = "Retrieved-Exception";

	@Autowired
	private Resilience4JCircuitBreakerFactory factory;

	@Test
	public void r4jFilterServiceUnavailable() {
		testClient.get()
			.uri("/delay/3")
			.header("Host", "www.sccbfailure.org")
			.exchange()
			.expectStatus()
			.isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
	}

	@Test
	public void r4jFilterExceptionFallback() {
		testClient.get()
			.uri("/delay/3")
			.header("Host", "www.circuitbreakerexceptionfallback.org")
			.exchange()
			.expectStatus()
			.isOk()
			.expectHeader()
			.value(RETRIEVED_EXCEPTION, containsString("TimeoutException"));
	}

	@Test
	public void cbFilterTimesoutMessage() {
		testClient.get()
			.uri("/delay/3")
			.header("Host", "www.sccbtimeout.org")
			.exchange()
			.expectStatus()
			.isEqualTo(HttpStatus.GATEWAY_TIMEOUT)
			.expectBody()
			.jsonPath("$.status")
			.isEqualTo(String.valueOf(HttpStatus.GATEWAY_TIMEOUT.value()))
			.jsonPath("$.message")
			.value(containsString("1000ms"));
	}

	@Test
	public void toStringFormat() {
		SpringCloudCircuitBreakerFilterFactory.Config config = new SpringCloudCircuitBreakerFilterFactory.Config()
			.setName("myname")
			.setFallbackUri("forward:/myfallback");
		GatewayFilter filter = new SpringCloudCircuitBreakerResilience4JFilterFactory(
				new ReactiveResilience4JCircuitBreakerFactory(CircuitBreakerRegistry.ofDefaults(),
						TimeLimiterRegistry.ofDefaults()),
				null)
			.apply(config);
		assertThat(filter.toString()).contains("myname").contains("forward:/myfallback");
	}

	@Test
	public void testHeadersAreClearedOnFallback() {
		testClient.post()
			.uri("/responseheaders/502")
			.body(BodyInserters.fromFormData("name-1", "value-1"))
			.header("Host", "www.circuitbreakerresetexchange.org")
			.header("X-Test-Header-1", "value1")
			.accept(MediaType.APPLICATION_JSON)
			.exchange()
			.expectStatus()
			.isOk()
			.expectHeader()
			.doesNotExist("X-Test-Header-1")
			.expectHeader()
			.valueEquals("X-Test-Header-1-fallback", "value1");
	}

	@EnableAutoConfiguration
	@SpringBootConfiguration
	@Import(BaseWebClientTests.DefaultTestConfig.class)
	@RestController
	static class Config extends SpringCloudCircuitBreakerTestConfig {

		@Bean
		public Customizer<ReactiveResilience4JCircuitBreakerFactory> slowCusomtizer() {
			return factory -> {
				factory.addCircuitBreakerCustomizer(cb -> cb.transitionToForcedOpenState(), "failcmd");
			};
		}

	}

}
