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

package org.springframework.cloud.gateway.server.mvc.config;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.server.mvc.test.HttpbinTestcontainers;
import org.springframework.cloud.gateway.server.mvc.test.PermitAllSecurityConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.client.RestTestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

/**
 * Integration tests for Route-specific CORS configuration in Spring Cloud Gateway MVC.
 *
 * @author Fatih Celik
 */
@SpringBootTest(webEnvironment = RANDOM_PORT)
@ActiveProfiles("cors-mvc")
@ContextConfiguration(initializers = HttpbinTestcontainers.class)
class CorsPerRouteMvcTests {

	@Autowired
	private RestTestClient testClient;

	@Test
	void testPreFlightCorsRequest() {
		testClient.options()
			.uri("/cors-allowed/test")
			.header(HttpHeaders.ORIGIN, "https://domain.com")
			.header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
			.exchange()
			.expectStatus()
			.isOk()
			.expectHeader()
			.valueEquals(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "https://domain.com")
			.expectHeader()
			.valueEquals(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true")
			.expectHeader()
			.valueEquals(HttpHeaders.ACCESS_CONTROL_MAX_AGE, "30")
			.expectHeader()
			.valueMatches(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS, ".*GET.*POST.*")
			.expectBody()
			.isEmpty();
	}

	@Test
	void testActualCorsRequest() {
		testClient.get()
			.uri("/cors-allowed/test")
			.header(HttpHeaders.ORIGIN, "https://domain.com")
			.exchange()
			.expectStatus()
			.isOk()
			.expectHeader()
			.valueEquals(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "https://domain.com")
			.expectHeader()
			.valueEquals(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true")
			.expectBody()
			.jsonPath("$.url")
			.value(v -> {
				assertThat(v.toString()).contains("/anything/cors");
			});
	}

	@Test
	void testPreFlightForbiddenCorsRequest() {
		testClient.options()
			.uri("/cors-allowed/test")
			.header(HttpHeaders.ORIGIN, "https://malicious-domain.com")
			.header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
			.exchange()
			.expectStatus()
			.isForbidden();
	}

	@Test
	void testNoCorsMetadataShouldNotAddHeaders() {
		testClient.get()
			.uri("/no-cors/test")
			.header(HttpHeaders.ORIGIN, "https://any-domain.com")
			.exchange()
			.expectStatus()
			.isOk()
			.expectHeader()
			.doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN);
	}

	@Test
	void testPreFlightWithMultipleAllowedMethods() {
		testClient.options()
			.uri("/cors-allowed/test")
			.header(HttpHeaders.ORIGIN, "https://domain.com")
			.header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
			.exchange()
			.expectStatus()
			.isOk()
			.expectHeader()
			.valueMatches(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS, ".*GET.*POST.*");
	}

	@Test
	void testOriginPatternsAllowed() {
		testClient.get()
			.uri("/cors-pattern/test")
			.header(HttpHeaders.ORIGIN, "https://api.spring.io")
			.exchange()
			.expectStatus()
			.isOk()
			.expectHeader()
			.valueEquals(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "https://api.spring.io");
	}

	@Test
	void testActualCorsRequestForbidden() {
		testClient.get()
			.uri("/cors-allowed/test")
			.header(HttpHeaders.ORIGIN, "https://malicious-domain.com")
			.exchange()
			.expectStatus()
			.isForbidden();
	}

	@Test
	void testPreFlightCustomHeaders() {
		testClient.options()
			.uri("/cors-allowed/test")
			.header(HttpHeaders.ORIGIN, "https://domain.com")
			.header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
			.header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "X-Custom-Request-Header")
			.exchange()
			.expectStatus()
			.isOk()
			.expectHeader()
			.valueEquals(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS, "X-Custom-Request-Header")
			.expectHeader()
			.valueEquals(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS, "X-Custom-Exposed-Header");
	}

	@SpringBootConfiguration
	@EnableAutoConfiguration
	@Import(PermitAllSecurityConfiguration.class)
	static class TestConfig {

	}

}
