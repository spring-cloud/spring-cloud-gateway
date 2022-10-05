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

package org.springframework.cloud.gateway.filter.factory;

import java.util.Arrays;
import java.util.Map;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AddRequestHeadersIfNotPresentGatewayFilterFactory.KeyValue;
import org.springframework.cloud.gateway.filter.factory.AddRequestHeadersIfNotPresentGatewayFilterFactory.KeyValueConfig;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.cloud.gateway.test.BaseWebClientTests;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;
import static org.springframework.cloud.gateway.test.TestUtils.getMap;

@SpringBootTest(webEnvironment = RANDOM_PORT)
@DirtiesContext
@ActiveProfiles(profiles = "request-headers-if-not-present-web-filter")
public class AddRequestHeadersIfNotPresentGatewayFilterFactoryTests extends BaseWebClientTests {

	private static final String TEST_HEADER_1 = "X-Request-Example";

	private static final String TEST_HEADER_2 = "X-Request-Second-Example";

	private static final String TEST_HOST_HEADER_VALUE = "www.addrequestheaderjava.org";

	@Test
	public void addRequestHeadersIfHeaderPresentFilterDoesNotAddHeaderIfPresent() {
		final String initialHeaderValue = "initial-value";
		testClient.get().uri("/headers").header(TEST_HEADER_1, initialHeaderValue).exchange().expectBody(Map.class)
				.consumeWith(result -> {
					Map<String, Object> headers = getMap(result.getResponseBody(), "headers");
					assertThat(headers).containsEntry(TEST_HEADER_1, initialHeaderValue);
				});
	}

	@Test
	public void addRequestHeadersIfNotPresentFilterWorks() {
		testClient.get().uri("/headers").exchange().expectBody(Map.class).consumeWith(result -> {
			Map<String, Object> headers = getMap(result.getResponseBody(), "headers");
			assertThat(headers).containsEntry(TEST_HEADER_1, "ValueA");
		});
	}

	@Test
	public void addRequestHeadersIfNotPresentFilterOnlyWorksFirstPassWhenMultipleValues() {
		testClient.get().uri("/multivalueheaders").exchange().expectBody(Map.class).consumeWith(result -> {
			Map<String, Object> headers = getMap(result.getResponseBody(), "headers");
			assertThat(headers).containsEntry(TEST_HEADER_1, Arrays.asList("ValueA"));
			assertThat(headers).containsEntry(TEST_HEADER_2, Arrays.asList("ValueC"));
		});
	}

	@Test
	public void addRequestHeadersIfNotPresentFilterWorksOnlyMissingValues() {
		final String existingValue = "existing-value";
		testClient.get().uri("/multivalueheaders").header(TEST_HEADER_2, existingValue).exchange().expectBody(Map.class)
				.consumeWith(result -> {
					Map<String, Object> headers = getMap(result.getResponseBody(), "headers");
					assertThat(headers).containsEntry(TEST_HEADER_1, Arrays.asList("ValueA"));
					assertThat(headers).containsEntry(TEST_HEADER_2, Arrays.asList(existingValue));
				});
	}

	@Test
	public void addRequestHeadersIfNotPresentFilterWorksJavaDsl() {
		testClient.get().uri("/headers").header("Host", TEST_HOST_HEADER_VALUE).exchange().expectBody(Map.class)
				.consumeWith(result -> {
					Map<String, Object> headers = getMap(result.getResponseBody(), "headers");
					assertThat(headers).containsEntry("X-Request-Acme", "ValueB-www");
				});
	}

	@Test
	public void addRequestHeadersIfNotPresentFilterMultipleValuesWorksJavaDsl() {
		testClient.get().uri("/multivalueheaders").header("Host", TEST_HOST_HEADER_VALUE).exchange()
				.expectBody(Map.class).consumeWith(result -> {
					Map<String, Object> headers = getMap(result.getResponseBody(), "headers");
					assertThat(headers).containsEntry("X-Request-Acme",
							Arrays.asList("ValueX", "ValueY", "ValueZ", "www"));
				});
	}

	@Test
	public void toStringFormat() {
		KeyValueConfig keyValueConfig = new KeyValueConfig();
		keyValueConfig.setKeyValues(new KeyValue[] { new KeyValue("my-header-name-1", "my-header-value-1"),
				new KeyValue("my-header-name-2", "my-header-value-2"), });
		GatewayFilter filter = new AddRequestHeadersIfNotPresentGatewayFilterFactory().apply(keyValueConfig);
		assertThat(filter.toString()).startsWith("[AddRequestHeadersIfNotPresent")
				.contains("my-header-name-1 = 'my-header-value-1'").contains("my-header-name-2 = 'my-header-value-2'")
				.endsWith("]");
	}

	@EnableAutoConfiguration
	@SpringBootConfiguration
	@Import(DefaultTestConfig.class)
	public static class TestConfig {

		@Value("${test.uri}")
		String uri;

		@Bean
		public RouteLocator testRouteLocator(RouteLocatorBuilder builder) {
			return builder.routes().route("add_request_headers_if_not_present_java_test",
					r -> r.path("/headers").and().host("{sub}.addrequestheaderjava.org")
							.filters(f -> f.addRequestHeadersIfNotPresent("X-Request-Acme:ValueB-{sub}")).uri(uri))
					.route("add_multiple_request_headers_java_test",
							r -> r.path("/multivalueheaders").and().host("{sub}.addrequestheaderjava.org")
									.filters(f -> f.addRequestHeadersIfNotPresent("X-Request-Acme:ValueX",
											"X-Request-Acme:ValueY", "X-Request-Acme:ValueZ", "X-Request-Acme:{sub}"))
									.uri(uri))
					.build();
		}

	}

}
