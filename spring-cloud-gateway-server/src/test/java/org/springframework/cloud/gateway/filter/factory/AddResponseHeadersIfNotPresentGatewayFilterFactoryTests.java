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

package org.springframework.cloud.gateway.filter.factory;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.cloud.gateway.support.config.KeyValue;
import org.springframework.cloud.gateway.support.config.KeyValueConfig;
import org.springframework.cloud.gateway.test.BaseWebClientTests;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

@SpringBootTest(webEnvironment = RANDOM_PORT)
@DirtiesContext
@ActiveProfiles(profiles = "response-headers-if-not-present-web-filter")
public class AddResponseHeadersIfNotPresentGatewayFilterFactoryTests extends BaseWebClientTests {

	private static final String TEST_HEADER_KEY1 = "X-Response-Example";

	private static final String TEST_HEADER_KEY2 = "X-Response-Second-Example";

	private static final String TEST_HEADER_VALUE_A = "ValueA";

	private static final String TEST_HEADER_VALUE_C = "ValueC";

	@Test
	public void responseHeadersPresent() {
		Map<String, String> body = new HashMap<>();
		final String headerValue1 = "ResponseHeadersPresent-Value1";
		body.put(TEST_HEADER_KEY1, headerValue1);

		testClient.patch()
			.uri("/headers")
			.header("Host", "www.addresponseheadersifnotpresenttest.org")
			.bodyValue(body)
			.exchange()
			.expectHeader()
			.valueEquals(TEST_HEADER_KEY1, headerValue1);
	}

	@Test
	public void responseHeadersNotPresentAndFilterWorks() {
		testClient.patch()
			.uri("/headers")
			.header("Host", "www.addresponseheadersifnotpresenttest.org")
			.bodyValue(new HashMap<>())
			.exchange()
			.expectHeader()
			.valueEquals(TEST_HEADER_KEY1, TEST_HEADER_VALUE_A);
	}

	@Test
	public void responseMultipleHeadersPresent() {
		Map<String, String> body = new HashMap<>();
		final String headerValue1 = "ResponseMultipleHeadersPresent-Value1";
		final String headerValue2 = "ResponseMultipleHeadersPresent-Value2";
		body.put(TEST_HEADER_KEY1, headerValue1);
		body.put(TEST_HEADER_KEY2, headerValue2);

		testClient.patch()
			.uri("/headers")
			.header("Host", "www.addmultipleresponseheadersifnotpresenttest.org")
			.bodyValue(body)
			.exchange()
			.expectHeader()
			.valueEquals(TEST_HEADER_KEY1, headerValue1)
			.expectHeader()
			.valueEquals(TEST_HEADER_KEY2, headerValue2);
	}

	@Test
	public void responseMultipleHeadersNotPresentAndFilterWorks() {
		testClient.patch()
			.uri("/headers")
			.header("Host", "www.addmultipleresponseheadersifnotpresenttest.org")
			.bodyValue(new HashMap<>())
			.exchange()
			.expectHeader()
			.valueEquals(TEST_HEADER_KEY1, TEST_HEADER_VALUE_A)
			.expectHeader()
			.valueEquals(TEST_HEADER_KEY2, TEST_HEADER_VALUE_C);
	}

	@Test
	public void responseMultipleHeadersPartialPresent() {
		Map<String, String> body = new HashMap<>();
		final String headerValue2 = "ResponseMultipleHeadersPartialPresent-Value2";
		body.put(TEST_HEADER_KEY2, headerValue2);

		testClient.patch()
			.uri("/headers")
			.header("Host", "www.addmultipleresponseheadersifnotpresenttest.org")
			.bodyValue(body)
			.exchange()
			.expectHeader()
			.valueEquals(TEST_HEADER_KEY1, TEST_HEADER_VALUE_A)
			.expectHeader()
			.valueEquals(TEST_HEADER_KEY2, headerValue2);
	}

	@Test
	public void responseHeadersNotPresentAndFilterWorksJavaDsl() {
		testClient.patch()
			.uri("/headers")
			.header("Host", "www.addresponseheadersifnotpresentjavadsl.org")
			.bodyValue(new HashMap<>())
			.exchange()
			.expectHeader()
			.valueEquals("X-Response-Java-Example", "Value-www");
	}

	@Test
	public void responseMultipleHeadersNotPresentAndFilterWorksJavaDsl() {
		testClient.patch()
			.uri("/headers")
			.header("Host", "www.addmultipleresponseheadersifnotpresentjavadsl.org")
			.bodyValue(new HashMap<>())
			.exchange()
			.expectHeader()
			.valueEquals("X-Response-Java-Example", "Value-www", "ValueX", "ValueY", "ValueZ");
	}

	@Test
	public void toStringFormat() {
		KeyValueConfig keyValueConfig = new KeyValueConfig();
		keyValueConfig.setKeyValues(new KeyValue[] { new KeyValue("X-Response-Key1", "Value1"),
				new KeyValue("X-Response-Key2", "Value2") });
		GatewayFilter filter = new AddResponseHeadersIfNotPresentGatewayFilterFactory().apply(keyValueConfig);
		assertThat(filter.toString()).startsWith("[AddResponseHeadersIfNotPresent")
			.contains("X-Response-Key1 = 'Value1'")
			.contains("X-Response-Key2 = 'Value2'")
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
			return builder.routes()
				.route("add_response_headers_if_not_present_java_test",
						r -> r.path("/headers")
							.and()
							.host("{sub}.addresponseheadersifnotpresentjavadsl.org")
							.filters(f -> f.addResponseHeadersIfNotPresent("X-Response-Java-Example:Value-{sub}"))
							.uri(uri))
				.route("add_multiple_response_headers_if_not_present_java_test",
						r -> r.path("/headers")
							.and()
							.host("{sub}.addmultipleresponseheadersifnotpresentjavadsl.org")
							.filters(f -> f.addResponseHeadersIfNotPresent("X-Response-Java-Example:Value-{sub}",
									"X-Response-Java-Example:ValueX", "X-Response-Java-Example:ValueY",
									"X-Response-Java-Example:ValueZ"))
							.uri(uri))
				.build();

		}

	}

}
