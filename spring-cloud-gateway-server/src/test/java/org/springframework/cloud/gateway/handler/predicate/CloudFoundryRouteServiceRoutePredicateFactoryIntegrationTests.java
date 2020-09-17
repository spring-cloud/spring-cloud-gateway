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

package org.springframework.cloud.gateway.handler.predicate;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.Test;
import org.junit.runner.RunWith;

import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.cloud.gateway.test.BaseWebClientTests;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.junit4.SpringRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

/**
 * @author Toshiaki Maki
 */
@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = RANDOM_PORT)
@DirtiesContext
public class CloudFoundryRouteServiceRoutePredicateFactoryIntegrationTests extends BaseWebClientTests {

	@LocalServerPort
	int port;

	@Test
	public void predicateWorkWithProperties() {
		testClient.get().uri("/").header("Host", "props.routeservice.example.com")
				.header("X-CF-Forwarded-Url", "http://localhost:" + port + "/actuator/health")
				.header("X-CF-Proxy-Signature", "foo").header("X-CF-Proxy-Metadata", "bar").exchange()
				.expectBody(JsonNode.class).consumeWith(r -> assertThat(r.getResponseBody().has("status")).isTrue());
	}

	@Test
	public void predicateWillNotWorkUnlessHeadersAreEnough() {
		testClient.get().uri("/").header("Host", "props.routeservice.example.com")
				.header("X-CF-Forwarded-Url", "http://localhost:" + port + "/actuator/health")
				.header("X-CF-Proxy-Metadata", "bar").exchange().expectStatus().isOk().expectHeader()
				.valueEquals(ROUTE_ID_HEADER, "default_path_to_httpbin");
	}

	@Test
	public void predicateWorkWithDsl() {
		testClient.get().uri("/").header("Host", "dsl.routeservice.example.com")
				.header("X-CF-Forwarded-Url", "http://localhost:" + port + "/actuator/health")
				.header("X-CF-Proxy-Signature", "foo").header("X-CF-Proxy-Metadata", "bar").exchange()
				.expectBody(JsonNode.class).consumeWith(r -> assertThat(r.getResponseBody().has("status")).isTrue());
	}

	@EnableAutoConfiguration
	@SpringBootConfiguration
	@Import(DefaultTestConfig.class)
	public static class TestConfig {

		@Bean
		public RouteLocator routeLocator(RouteLocatorBuilder builder) {
			return builder.routes()
					.route(r -> r.cloudFoundryRouteService().and().header("Host", "dsl.routeservice.example.com")
							.filters(f -> f.requestHeaderToRequestUri("X-CF-Forwarded-Url")).uri("https://example.com"))
					.build();
		}

	}

}
