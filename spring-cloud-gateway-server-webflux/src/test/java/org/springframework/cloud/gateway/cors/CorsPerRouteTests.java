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

package org.springframework.cloud.gateway.cors;

import java.util.Map;

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
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.test.annotation.DirtiesContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;
import static org.springframework.http.HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS;
import static org.springframework.http.HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN;
import static org.springframework.http.HttpHeaders.ACCESS_CONTROL_MAX_AGE;

@SpringBootTest(webEnvironment = RANDOM_PORT,
		// Will be combined by AbstractHandlerMapping, @see
		// AbstractHandlerMapping#getHandler,
		// therefore requires separate configuration
		properties = "spring.config.location=classpath:/application-cors-per-route-config.yml")
@DirtiesContext
public class CorsPerRouteTests extends BaseWebClientTests {

	@Test
	public void testPreFlightCorsRequest() {
		testClient.options()
			.uri("/abc")
			.header("Origin", "domain.com")
			.header("Access-Control-Request-Method", "GET")
			.exchange()
			.expectBody(Map.class)
			.consumeWith(result -> {
				assertThat(result.getResponseBody()).isNull();
				assertThat(result.getStatus()).isEqualTo(HttpStatus.OK);

				HttpHeaders responseHeaders = result.getResponseHeaders();
				assertThat(responseHeaders.getAccessControlAllowOrigin()).as(missingHeader(ACCESS_CONTROL_ALLOW_ORIGIN))
					.isEqualTo("domain.com");
				assertThat(responseHeaders.getAccessControlAllowMethods())
					.as(missingHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS))
					.containsExactlyInAnyOrder(HttpMethod.GET, HttpMethod.POST);
				assertThat(responseHeaders.getAccessControlMaxAge()).as(missingHeader(ACCESS_CONTROL_MAX_AGE))
					.isEqualTo(30L);
				assertThat(responseHeaders.getAccessControlAllowCredentials())
					.as(missingHeader(ACCESS_CONTROL_ALLOW_CREDENTIALS))
					.isEqualTo(true);
			});
	}

	@Test
	public void testPreFlightCorsRequestJavaConfig() {
		testClient.options()
			.uri("/route-test")
			.header("Origin", "another-domain.com")
			.header("Host", "www.javaconfhost.org")
			.header("Access-Control-Request-Method", "GET")
			.exchange()
			.expectBody(Map.class)
			.consumeWith(result -> {
				assertThat(result.getResponseBody()).isNull();
				assertThat(result.getStatus()).isEqualTo(HttpStatus.OK);

				HttpHeaders responseHeaders = result.getResponseHeaders();
				assertThat(responseHeaders.getAccessControlAllowOrigin()).as(missingHeader(ACCESS_CONTROL_ALLOW_ORIGIN))
					.isEqualTo("another-domain.com");
				assertThat(responseHeaders.getAccessControlAllowMethods())
					.as(missingHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS))
					.containsExactlyInAnyOrder(HttpMethod.GET);
				assertThat(responseHeaders.getAccessControlMaxAge()).as(missingHeader(ACCESS_CONTROL_MAX_AGE))
					.isEqualTo(50L);
			});
	}

	@Test
	public void testIndependentCorsConfigurationForSamePath() {
		// Test first route with same path but different cors config (via different host)
		testClient.options()
			.uri("/shared-path")
			.header("Origin", "route1-domain.com")
			.header("Host", "route1.host.example")
			.header("Access-Control-Request-Method", "GET")
			.exchange()
			.expectBody(Map.class)
			.consumeWith(result -> {
				assertThat(result.getResponseBody()).isNull();
				assertThat(result.getStatus()).isEqualTo(HttpStatus.OK);

				HttpHeaders responseHeaders = result.getResponseHeaders();
				assertThat(responseHeaders.getAccessControlAllowOrigin()).as(missingHeader(ACCESS_CONTROL_ALLOW_ORIGIN))
					.isEqualTo("route1-domain.com");
				assertThat(responseHeaders.getAccessControlAllowMethods())
					.as(missingHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS))
					.containsExactlyInAnyOrder(HttpMethod.GET);
				assertThat(responseHeaders.getAccessControlMaxAge()).as(missingHeader(ACCESS_CONTROL_MAX_AGE))
					.isEqualTo(100L);
			});

		// Test second route with same path but different cors config (via different host)
		testClient.options()
			.uri("/shared-path")
			.header("Origin", "route2-domain.com")
			.header("Host", "route2.host.example")
			.header("Access-Control-Request-Method", "POST")
			.exchange()
			.expectBody(Map.class)
			.consumeWith(result -> {
				assertThat(result.getResponseBody()).isNull();
				assertThat(result.getStatus()).isEqualTo(HttpStatus.OK);

				HttpHeaders responseHeaders = result.getResponseHeaders();
				assertThat(responseHeaders.getAccessControlAllowOrigin()).as(missingHeader(ACCESS_CONTROL_ALLOW_ORIGIN))
					.isEqualTo("route2-domain.com");
				assertThat(responseHeaders.getAccessControlAllowMethods())
					.as(missingHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS))
					.containsExactlyInAnyOrder(HttpMethod.POST);
				assertThat(responseHeaders.getAccessControlMaxAge()).as(missingHeader(ACCESS_CONTROL_MAX_AGE))
					.isEqualTo(200L);
			});
	}

	@Test
	public void testPreFlightForbiddenCorsRequest() {
		testClient.options()
			.uri("/cors")
			.header("Origin", "domain.com")
			.header("Access-Control-Request-Method", "GET")
			.exchange()
			.expectBody(Map.class)
			.consumeWith(result -> {
				assertThat(result.getResponseBody()).isNull();
				assertThat(result.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
			});
	}

	@Test
	public void testCorsValidatedRequest() {
		testClient.get()
			.uri("/cors/status/201")
			.header("Origin", "https://test.com")
			.exchange()
			.expectBody(String.class)
			.consumeWith(result -> {
				assertThat(result.getResponseBody()).endsWith("201");
				assertThat(result.getStatus()).isEqualTo(HttpStatus.CREATED);
			});
	}

	private String missingHeader(String accessControlAllowOrigin) {
		return "Missing header value in response: " + accessControlAllowOrigin;
	}

	@EnableAutoConfiguration
	@SpringBootConfiguration
	@Import(DefaultTestConfig.class)
	public static class TestConfig {

		@Value("${test.uri}")
		String uri;

		@Bean
		public RouteLocator testRouteLocator(RouteLocatorBuilder builder) {
			return builder.routes()
				.route("cors_route_java_test",
						r -> r.host("*.javaconfhost.org")
							.and()
							.path("/route-test/**")
							.filters(f -> f.stripPrefix(1).prefixPath("/httpbin"))
							.metadata(Map.of("cors",
									Map.of("allowedOrigins", "another-domain.com", "allowedMethods",
											HttpMethod.GET.name(), "maxAge", 50)))
							.uri(uri))
				.route("cors_route_same_path_1",
						r -> r.host("route1.host.example")
							.and()
							.path("/shared-path/**")
							.filters(f -> f.stripPrefix(1).prefixPath("/httpbin"))
							.metadata(Map.of("cors",
									Map.of("allowedOrigins", "route1-domain.com", "allowedMethods",
											HttpMethod.GET.name(), "maxAge", 100)))
							.uri(uri))
				.route("cors_route_same_path_2",
						r -> r.host("route2.host.example")
							.and()
							.path("/shared-path/**")
							.filters(f -> f.stripPrefix(1).prefixPath("/httpbin"))
							.metadata(Map.of("cors",
									Map.of("allowedOrigins", "route2-domain.com", "allowedMethods",
											HttpMethod.POST.name(), "maxAge", 200)))
							.uri(uri))
				.build();
		}

	}

}
