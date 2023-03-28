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

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.cloud.gateway.test.BaseWebClientTests;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.util.TestSocketUtils;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

@SpringBootTest(webEnvironment = RANDOM_PORT)
@AutoConfigureWebTestClient
public class RoutePredicateHandlerMappingWithAutoconfiguredClientIntegrationTests {

	@Autowired
	WebTestClient webTestClient;

	@BeforeAll
	static void beforeClass() {
		int managementPort = TestSocketUtils.findAvailableTcpPort();
		System.setProperty("management.server.port", String.valueOf(managementPort));
	}

	@AfterAll
	static void afterClass() {
		System.clearProperty("management.server.port");
	}

	@Test
	void shouldReturnOk() {
		this.webTestClient.get().uri("/get").exchange().expectStatus().isOk();
	}

	@EnableAutoConfiguration
	@SpringBootConfiguration
	@Import(BaseWebClientTests.DefaultTestConfig.class)
	@RestController
	public static class TestConfig {

		@Value("${test.uri:http://httpbin.org:80}")
		String uri;

		@Bean
		RouteLocator testRoutes(RouteLocatorBuilder builder) {
			return builder.routes().route(r -> r.path("/get").filters(f -> f.prefixPath("/httpbin")).uri(uri)).build();
		}

	}

}
