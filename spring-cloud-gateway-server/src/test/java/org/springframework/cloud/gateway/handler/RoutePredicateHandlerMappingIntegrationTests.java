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
import org.springframework.test.util.TestSocketUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.DefaultUriBuilderFactory;

import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

@SpringBootTest(webEnvironment = RANDOM_PORT, properties = "management.server.port=${test.port}")
@DirtiesContext
public class RoutePredicateHandlerMappingIntegrationTests extends BaseWebClientTests {

	private static int managementPort;

	@BeforeAll
	public static void beforeClass() {
		managementPort = TestSocketUtils.findAvailableTcpPort();
		System.setProperty("test.port", String.valueOf(managementPort));
	}

	@AfterAll
	public static void afterClass() {
		System.clearProperty("test.port");
	}

	@Test
	public void requestsToManagementPortReturn404() {
		String baseUrl = "http://localhost:" + managementPort;
		testClient.mutate()
			.uriBuilderFactory(new DefaultUriBuilderFactory(baseUrl))
			.baseUrl(baseUrl)
			.build()
			.get()
			.uri("/get")
			.exchange()
			.expectStatus()
			.isNotFound();
	}

	@Test
	public void requestsToManagementPortAndHostHeaderReturn404() {
		String host = "example.com:8888";
		String baseUrl = "http://localhost:" + managementPort;
		testClient.mutate()
			.uriBuilderFactory(new DefaultUriBuilderFactory(baseUrl))
			.baseUrl(baseUrl)
			.build()
			.get()
			.uri("/get")
			.header("host", host)
			.exchange()
			.expectStatus()
			.isNotFound();
	}

	@Test
	public void andNotWorksWithMissingParameter() {
		testClient.get().uri("/andnotquery").exchange().expectBody(String.class).isEqualTo("notsupplied");
	}

	@Test
	public void andNotWorksWithParameter() {
		testClient.get()
			.uri("/andnotquery?myquery=shouldnotsee")
			.exchange()
			.expectBody(String.class)
			.isEqualTo("hasquery");
	}

	@Test
	public void andNestedOrQuery1() {
		testClient.get()
			.uri("/andnestedquery?query1=hasquery1")
			.exchange()
			.expectBody(String.class)
			.isEqualTo("hasquery1,notsupplied");
	}

	@Test
	public void andNestedOrQuery2() {
		testClient.get()
			.uri("/andnestedquery?query2=hasquery2")
			.exchange()
			.expectBody(String.class)
			.isEqualTo("notsupplied,hasquery2");
	}

	@EnableAutoConfiguration
	@SpringBootConfiguration
	@Import(DefaultTestConfig.class)
	@RestController
	public static class TestConfig {

		@Value("${test.uri:http://httpbin.org:80}")
		String uri;

		@GetMapping("/httpbin/andnotquery")
		String andnotquery(@RequestParam(name = "myquery", defaultValue = "notsupplied") String myquery) {
			return myquery;
		}

		@GetMapping("/httpbin/andnestedquery")
		String andnotquery(@RequestParam(name = "query1", defaultValue = "notsupplied") String query1,
				@RequestParam(name = "query2", defaultValue = "notsupplied") String query2) {
			return query1 + "," + query2;
		}

		@GetMapping("/httpbin/hasquery")
		String hasquery() {
			return "hasquery";
		}

		@Bean
		RouteLocator testRouteLocator(RouteLocatorBuilder builder) {
			return builder.routes()
				.route("and_not_missing_myquery",
						r -> r.path("/andnotquery")
							.and()
							.not(p -> p.query("myquery"))
							.filters(f -> f.prefixPath("/httpbin"))
							.uri(uri))
				.route("and_not_has_myquery",
						r -> r.path("/andnotquery")
							.and()
							.query("myquery")
							.filters(f -> f.setPath("/httpbin/hasquery"))
							.uri(uri))
				.route("and_nested_query1_or_query2",
						r -> r.path("/andnestedquery")
							.and()
							.nested(p -> p.query("query1").or().query("query2"))
							.filters(f -> f.prefixPath("/httpbin"))
							.uri(uri))
				.build();
		}

	}

}
