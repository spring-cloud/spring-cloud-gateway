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

package org.springframework.cloud.gateway.server.mvc.filter;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.server.mvc.test.HttpbinTestcontainers;
import org.springframework.cloud.gateway.server.mvc.test.HttpbinUriResolver;
import org.springframework.cloud.gateway.server.mvc.test.PermitAllSecurityConfiguration;
import org.springframework.cloud.gateway.server.mvc.test.TestLoadBalancerConfig;
import org.springframework.cloud.loadbalancer.annotation.LoadBalancerClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;
import static org.springframework.cloud.gateway.server.mvc.filter.AfterFilterFunctions.modifyResponseBody;
import static org.springframework.cloud.gateway.server.mvc.filter.AfterFilterFunctions.removeJsonAttributesResponseBody;
import static org.springframework.cloud.gateway.server.mvc.handler.GatewayRouterFunctions.route;
import static org.springframework.cloud.gateway.server.mvc.handler.HandlerFunctions.http;

/**
 * @author raccoonback
 */
@SpringBootTest(webEnvironment = RANDOM_PORT)
@ContextConfiguration(initializers = HttpbinTestcontainers.class)
class AfterFilterFunctionsTests {

	@Autowired
	RestTestClient restClient;

	@Test
	void doesNotRemoveJsonAttributes() {
		restClient.get()
			.uri("/anything/does_not/remove_json_attributes")
			.exchange()
			.expectStatus()
			.isOk()
			.expectBody(Map.class)
			.consumeWith(res -> {
				assertThat(res.getResponseBody()).containsEntry("foo", "bar");
				assertThat(res.getResponseBody()).containsEntry("baz", "qux");
			});
	}

	@Test
	void removeJsonAttributesToAvoidBeingRecursive() {
		restClient.get()
			.uri("/anything/remove_json_attributes_to_avoid_being_recursive")
			.exchange()
			.expectStatus()
			.isOk()
			.expectBody(Map.class)
			.consumeWith(res -> {
				assertThat(res.getResponseBody()).doesNotContainKey("foo");
				assertThat(res.getResponseBody()).containsEntry("baz", "qux");
			});
	}

	@Test
	void removeJsonAttributesRecursively() {
		restClient.get()
			.uri("/anything/remove_json_attributes_recursively")
			.exchange()
			.expectStatus()
			.isOk()
			.expectBody(Map.class)
			.consumeWith(res -> {
				assertThat(res.getResponseBody()).containsKey("foo");
				assertThat((Map<String, String>) res.getResponseBody().get("foo")).containsEntry("bar", "A");
				assertThat(res.getResponseBody()).containsEntry("quux", "C");
				assertThat(res.getResponseBody()).doesNotContainKey("qux");
			});
	}

	@Test
	void raisedErrorWhenRemoveJsonAttributes() {
		restClient.get()
			.uri("/anything/raised_error_when_remove_json_attributes")
			.exchange()
			.expectStatus()
			.is5xxServerError()
			.expectBody(String.class)
			.consumeWith(res -> {
				assertThat(res.getResponseBody()).isEqualTo("Failed to process JSON of response body.");
			});
	}

	@SpringBootConfiguration
	@EnableAutoConfiguration
	@LoadBalancerClient(name = "httpbin", configuration = TestLoadBalancerConfig.Httpbin.class)
	@Import(PermitAllSecurityConfiguration.class)
	protected static class TestConfiguration {

		@Bean
		public RouterFunction<ServerResponse> doesNotRemoveJsonAttributes() {
			// @formatter:off
			return route("does_not_remove_json_attributes")
					.GET("/anything/does_not/remove_json_attributes", http())
					.before(new HttpbinUriResolver())
					.after(
							removeJsonAttributesResponseBody(List.of("quux"), true)
					)
					.after(
							modifyResponseBody(
									String.class,
									String.class,
									MediaType.APPLICATION_JSON_VALUE,
									(request, response, s) -> "{\"foo\": \"bar\", \"baz\": \"qux\"}"
							)
					)
					.build();
			// @formatter:on
		}

		@Bean
		public RouterFunction<ServerResponse> removeJsonAttributesToAvoidBeingRecursively() {
			// @formatter:off
			return route("remove_json_attributes_to_avoid_being_recursive")
				.GET("/anything/remove_json_attributes_to_avoid_being_recursive", http())
				.before(new HttpbinUriResolver())
				.after(
						removeJsonAttributesResponseBody(List.of("foo"), false)
				)
				.after(
						modifyResponseBody(
								String.class,
								String.class,
								MediaType.APPLICATION_JSON_VALUE,
								(request, response, s) -> "{\"foo\": \"bar\", \"baz\": \"qux\"}"
						)
				)
				.build();
			// @formatter:on
		}

		@Bean
		public RouterFunction<ServerResponse> removeJsonAttributesRecursively() {
			// @formatter:off
			return route("remove_json_attributes_recursively")
					.GET("/anything/remove_json_attributes_recursively", http())
					.before(new HttpbinUriResolver())
					.after(
							removeJsonAttributesResponseBody(List.of("qux"), true)
					)
					.after(
							modifyResponseBody(
									String.class,
									String.class,
									MediaType.APPLICATION_JSON_VALUE,
									(request, response, s) -> "{\"foo\": { \"bar\": \"A\", \"qux\": \"B\"}, \"quux\": \"C\", \"qux\": {\"corge\": \"D\"}}"
							)
					)
					.build();
			// @formatter:on
		}

		@Bean
		public RouterFunction<ServerResponse> raisedErrorWhenRemoveJsonAttributes() {
			// @formatter:off
			return route("raised_error_when_remove_json_attributes")
					.GET("/anything/raised_error_when_remove_json_attributes", http())
					.before(new HttpbinUriResolver())
					.after(
							removeJsonAttributesResponseBody(List.of("qux"), true)
					)
					.after(
							modifyResponseBody(
									String.class,
									String.class,
									MediaType.APPLICATION_JSON_VALUE,
									(request, response, s) -> "{\"invalid_json\": 123"
							)
					)
					.build();
			// @formatter:on
		}

		@ControllerAdvice
		public class GlobalExceptionHandler {

			@ExceptionHandler(IllegalStateException.class)
			public ResponseEntity<String> handleIllegalException(IllegalStateException ex) {
				return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
			}

		}

	}

}
