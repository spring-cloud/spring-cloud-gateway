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

package org.springframework.cloud.gateway.filter.factory;

import org.junit.jupiter.api.Test;

import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.cloud.gateway.test.BaseWebClientTests;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;

import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

/**
 * @author Stepan Mikhailiuk
 */
@SpringBootTest(webEnvironment = RANDOM_PORT)
@DirtiesContext
public class SetRequestUriGatewayFilterFactoryIntegrationTests extends BaseWebClientTests {

	@LocalServerPort
	int port;

	@Test
	public void setUriWorkWithProperties() {
		testClient.get().uri("/").header("Host", "testservice.setrequesturi.org").exchange().expectStatus().isOk();

		testClient.get()
			.uri("/service/testservice")
			.header("Host", "setrequesturi.org")
			.exchange()
			.expectStatus()
			.isOk();
	}

	@EnableAutoConfiguration
	@SpringBootConfiguration
	@Import(DefaultTestConfig.class)
	public static class TestConfig {

		@Bean
		public RouteLocator routeLocator(RouteLocatorBuilder builder) {
			return builder.routes()
				.route("map_subdomain_to_service_name",
						r -> r.host("{serviceName}.setrequesturi.org")
							.filters(f -> f.prefixPath("/httpbin").setRequestUri("lb://{serviceName}"))
							.uri("no://op"))
				.route("map_path_to_service_name",
						r -> r.host("setrequesturi.org")
							.and()
							.path("/service/{serviceName}")
							.filters(f -> f.rewritePath("/.*", "/").setRequestUri("lb://{serviceName}"))
							.uri("no://op"))
				.build();
		}

	}

}
