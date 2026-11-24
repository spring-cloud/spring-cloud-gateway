/*
 * Copyright 2013-present the original author or authors.
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

package org.springframework.cloud.gateway.filter.headers.observation;

import java.time.Duration;

import io.micrometer.core.instrument.LongTaskTimer;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.Test;
import reactor.core.Disposable;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.cloud.gateway.test.BaseWebClientTests;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

/**
 * Integration tests for stopping the gateway client observation of cancelled requests,
 * see <a href=
 * "https://github.com/spring-cloud/spring-cloud-gateway/issues/3609">gh-3609</a>.
 *
 * @author Kai Gülzau
 */
@SpringBootTest(webEnvironment = RANDOM_PORT,
		properties = "management.metrics.observations.include-active-long-task-timer=true")
@DirtiesContext
class ObservationClosingGlobalFilterIntegrationTests extends BaseWebClientTests {

	private static final String ROUTE_ID = "observation_cancel_test";

	@Autowired
	private MeterRegistry meterRegistry;

	@Test
	void activeClientRequestIsStoppedWhenRequestIsCancelledBeforeResponseHeaders() {
		int before = activeClientRequests();
		Disposable request = webClient.get()
			.uri("/delay/5")
			.header("Host", "www.observationcancel.org")
			.retrieve()
			.toBodilessEntity()
			.subscribe();
		await().atMost(Duration.ofSeconds(5))
			.untilAsserted(() -> assertThat(activeClientRequests()).isEqualTo(before + 1));

		// the client gives up while the gateway still waits for the response headers
		request.dispose();

		await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> assertThat(activeClientRequests()).isEqualTo(before));
	}

	@Test
	void activeClientRequestIsStoppedWhenRequestCompletes() {
		int before = activeClientRequests();
		webClient.get()
			.uri("/delay/0")
			.header("Host", "www.observationcancel.org")
			.retrieve()
			.toBodilessEntity()
			.block(Duration.ofSeconds(5));

		await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> assertThat(activeClientRequests()).isEqualTo(before));
	}

	private int activeClientRequests() {
		return meterRegistry.find("http.client.requests.active")
			.tag("spring.cloud.gateway.route.id", ROUTE_ID)
			.longTaskTimers()
			.stream()
			.mapToInt(LongTaskTimer::activeTasks)
			.sum();
	}

	@EnableAutoConfiguration
	@SpringBootConfiguration
	@Import(DefaultTestConfig.class)
	static class TestConfig {

		@Value("${test.uri}")
		private String testUri;

		@Bean
		RouteLocator observationCancelRouteLocator(RouteLocatorBuilder builder) {
			return builder.routes().route(ROUTE_ID, r -> r.host("**.observationcancel.org").uri(testUri)).build();
		}

	}

}
