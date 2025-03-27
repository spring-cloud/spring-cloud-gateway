/*
 * Copyright 2013-2025 the original author or authors.
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

import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.cloud.gateway.server.mvc.test.HttpbinTestcontainers;
import org.springframework.cloud.gateway.server.mvc.test.TestLoadBalancerConfig;
import org.springframework.cloud.gateway.server.mvc.test.client.TestRestClient;
import org.springframework.cloud.loadbalancer.annotation.LoadBalancerClient;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.cloud.gateway.server.mvc.filter.FilterFunctions.stripPrefix;
import static org.springframework.cloud.gateway.server.mvc.filter.FilterFunctions.uri;
import static org.springframework.cloud.gateway.server.mvc.handler.GatewayRouterFunctions.route;
import static org.springframework.cloud.gateway.server.mvc.handler.HandlerFunctions.http;
import static org.springframework.cloud.gateway.server.mvc.test.TestUtils.getMap;

@SuppressWarnings("unchecked")
@SpringBootTest(properties = {}, webEnvironment = WebEnvironment.RANDOM_PORT)
@ActiveProfiles("stripprefixstaticport")
@ContextConfiguration(initializers = HttpbinTestcontainers.class)
public class StripPrefixStaticPortTests {

	@Autowired
	TestRestClient restClient;

	@BeforeAll
	static void beforeAll() {
		HttpbinTestcontainers.initializeSystemProperties();
	}

	@Test
	public void stripPrefixStaticPort() {
		restClient.get()
			.uri("/long/path/to/anything/staticport")
			.exchange()
			.expectStatus()
			.isOk()
			.expectBody(Map.class)
			.consumeWith(res -> {
				Map<String, Object> map = res.getResponseBody();
				Map<String, Object> headers = getMap(map, "headers");
				assertThat(headers)
					.containsKeys(XForwardedRequestHeadersFilter.X_FORWARDED_PREFIX_HEADER,
							XForwardedRequestHeadersFilter.X_FORWARDED_HOST_HEADER,
							XForwardedRequestHeadersFilter.X_FORWARDED_PORT_HEADER,
							XForwardedRequestHeadersFilter.X_FORWARDED_PROTO_HEADER,
							XForwardedRequestHeadersFilter.X_FORWARDED_FOR_HEADER)
					.containsEntry(XForwardedRequestHeadersFilter.X_FORWARDED_PREFIX_HEADER, "/long/path/to");
			});
	}

	@Test
	public void stripPrefixStaticPortDsl() {
		restClient.get()
			.uri("/long/path/to/anything/staticportdsl")
			.exchange()
			.expectStatus()
			.isOk()
			.expectBody(Map.class)
			.consumeWith(res -> {
				Map<String, Object> map = res.getResponseBody();
				Map<String, Object> headers = getMap(map, "headers");
				assertThat(headers)
					.containsKeys(XForwardedRequestHeadersFilter.X_FORWARDED_PREFIX_HEADER,
							XForwardedRequestHeadersFilter.X_FORWARDED_HOST_HEADER,
							XForwardedRequestHeadersFilter.X_FORWARDED_PORT_HEADER,
							XForwardedRequestHeadersFilter.X_FORWARDED_PROTO_HEADER,
							XForwardedRequestHeadersFilter.X_FORWARDED_FOR_HEADER)
					.containsEntry(XForwardedRequestHeadersFilter.X_FORWARDED_PREFIX_HEADER, "/long/path/to");
			});
	}

	@SpringBootConfiguration
	@EnableAutoConfiguration
	@LoadBalancerClient(name = "httpbin", configuration = TestLoadBalancerConfig.Httpbin.class)
	protected static class TestConfiguration {

		@Bean
		StaticPortController staticPortController() {
			return new StaticPortController();
		}

		@Bean
		public RouterFunction<ServerResponse> gatewayRouterFunctionsStripPrefixStaticPortDsl(Environment env) {
			// @formatter:off
			return route("teststripprefixstaticportdsl")
					.GET("/long/path/to/anything/staticportdsl", http())
					.filter(uri(env.getProperty("strip.prefix.static.uri")))
					.filter(stripPrefix(3))
					.build();
			// @formatter:on
		}

	}

	@RestController
	protected static class StaticPortController {

		@GetMapping(path = "/anything/staticport", produces = MediaType.APPLICATION_JSON_VALUE)
		public ResponseEntity<?> messageEvents() {
			return ResponseEntity.status(HttpStatus.NOT_ACCEPTABLE).build();
		}

	}

}
