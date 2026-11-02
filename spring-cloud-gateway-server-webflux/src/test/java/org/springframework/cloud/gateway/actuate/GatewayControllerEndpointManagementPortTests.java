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
import java.util.Collections;

import org.junit.jupiter.api.Test;

import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.cloud.gateway.filter.FilterDefinition;
import org.springframework.cloud.gateway.handler.predicate.PredicateDefinition;
import org.springframework.cloud.gateway.route.RouteDefinition;
import org.springframework.cloud.gateway.test.PermitAllSecurityConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.BodyInserters;

import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

@SpringBootTest(
		properties = { "management.endpoint.gateway.enabled=true", "management.endpoints.web.exposure.include=*",
				"spring.cloud.gateway.server.webflux.actuator.verbose.enabled=true", "management.server.port=0" },
		webEnvironment = RANDOM_PORT)
class GatewayControllerEndpointManagementPortTests {

	@LocalManagementPort
	int managementPort;

	@Test
	void notExistingPredicateReturnsMessage() {
		RouteDefinition route = new RouteDefinition();
		route.setUri(URI.create("http://example.org"));
		route.setPredicates(Collections.singletonList(new PredicateDefinition("NotExistingPredicate=test-config")));
		assertBadRequest(route, "Invalid PredicateDefinition: [NotExistingPredicate]");
	}

	@Test
	void notExistingFilterReturnsMessage() {
		RouteDefinition route = new RouteDefinition();
		route.setUri(URI.create("http://example.org"));
		route.setFilters(Collections.singletonList(new FilterDefinition("NotExistingFilter=test-config")));
		assertBadRequest(route, "Invalid FilterDefinition: [NotExistingFilter]");
	}

	private void assertBadRequest(RouteDefinition route, String message) {
		WebTestClient.bindToServer()
			.baseUrl("http://localhost:" + this.managementPort)
			.build()
			.post()
			.uri("/actuator/gateway/routes/test-route")
			.accept(MediaType.APPLICATION_JSON)
			.body(BodyInserters.fromValue(route))
			.exchange()
			.expectStatus()
			.isBadRequest()
			.expectBody()
			.jsonPath("$.message")
			.isEqualTo(message);
	}

	@SpringBootConfiguration
	@EnableAutoConfiguration
	@Import(PermitAllSecurityConfiguration.class)
	static class TestConfig {

	}

}
