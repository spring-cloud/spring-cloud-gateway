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

package org.springframework.cloud.gateway.actuate;

import java.net.URI;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.cloud.gateway.handler.predicate.PredicateDefinition;
import org.springframework.cloud.gateway.route.RouteDefinition;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.BodyInserters;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

/**
 * @author Peter Müller
 */
@SpringBootTest(properties = { "management.endpoint.gateway.enabled=true",
		"management.endpoints.web.exposure.include=*", "spring.cloud.gateway.actuator.verbose.enabled=true" },
		webEnvironment = RANDOM_PORT)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@ActiveProfiles("redis-route-repository")
@Testcontainers
@Tag("DockerRequired")
public class GatewayControllerEndpointRedisRefreshTest {

	@Container
	public static GenericContainer redis = new GenericContainer<>("redis:5.0.14-alpine").withExposedPorts(6379);

	@BeforeAll
	public static void startRedisContainer() {
		redis.start();
	}

	@DynamicPropertySource
	static void containerProperties(DynamicPropertyRegistry registry) {
		registry.add("spring.data.redis.host", redis::getHost);
		registry.add("spring.data.redis.port", redis::getFirstMappedPort);
	}

	@Autowired
	WebTestClient testClient;

	@LocalServerPort
	int port;

	@Test
	public void testCorsConfigurationAfterReload() {
		Map<String, Object> cors = new HashMap<>();
		cors.put("allowCredentials", false);
		cors.put("allowedOrigins", "*");
		cors.put("allowedMethods", "GET");

		createOrUpdateRouteWithCors(cors);

		Awaitility.await().atMost(Duration.ofSeconds(3)).untilAsserted(() -> assertRouteHasCorsConfig(cors));
		Awaitility.await().atMost(Duration.ofSeconds(3)).untilAsserted(() -> assertPreflightAllowOrigin("*"));

		cors.put("allowedOrigins", "http://example.org");
		createOrUpdateRouteWithCors(cors);

		Awaitility.await().atMost(Duration.ofSeconds(3)).untilAsserted(() -> assertRouteHasCorsConfig(cors));
		Awaitility.await()
			.atMost(Duration.ofSeconds(3))
			.untilAsserted(() -> assertPreflightAllowOrigin("http://example.org"));
	}

	void createOrUpdateRouteWithCors(Map<String, Object> cors) {
		RouteDefinition testRouteDefinition = new RouteDefinition();
		testRouteDefinition.setUri(URI.create("http://example.org"));

		PredicateDefinition methodRoutePredicateDefinition = new PredicateDefinition("Method=GET");
		testRouteDefinition.setPredicates(List.of(methodRoutePredicateDefinition));

		testRouteDefinition.setMetadata(Map.of("cors", cors));

		testClient.post()
			.uri("http://localhost:" + port + "/actuator/gateway/routes/cors-test-route")
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
	}

	void assertRouteHasCorsConfig(Map<String, Object> cors) {
		testClient.get()
			.uri("http://localhost:" + port + "/actuator/gateway/routes/cors-test-route")
			.exchange()
			.expectStatus()
			.isOk()
			.expectBody()
			.jsonPath("$.metadata")
			.value(map -> assertThat((Map<String, Object>) map).hasSize(1).containsEntry("cors", cors));
	}

	void assertPreflightAllowOrigin(String origin) {
		testClient.options()
			.uri("http://localhost:" + port + "/")
			.header("Origin", "http://example.org")
			.header("Access-Control-Request-Method", "GET")
			.exchange()
			.expectStatus()
			.isOk()
			.expectHeader()
			.valueEquals("Access-Control-Allow-Origin", origin);
	}

}
