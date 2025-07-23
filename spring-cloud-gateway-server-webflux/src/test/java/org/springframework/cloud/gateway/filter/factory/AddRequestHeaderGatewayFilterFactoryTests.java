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
import org.springframework.cloud.gateway.filter.factory.AbstractNameValueGatewayFilterFactory.NameValueConfig;
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

/**
 * @author Spencer Gibb
 * @author Biju Kunjummen
 */
@SpringBootTest(webEnvironment = RANDOM_PORT)
@DirtiesContext
@ActiveProfiles(profiles = "request-header-web-filter")
public class AddRequestHeaderGatewayFilterFactoryTests extends BaseWebClientTests {

	@Test
	public void addRequestHeaderFilterWorks() {
		testClient.get()
			.uri("/headers")
			.header("Host", "www.addrequestheader.org")
			.exchange()
			.expectBody(Map.class)
			.consumeWith(result -> {
				Map<String, Object> headers = getMap(result.getResponseBody(), "headers");
				assertThat(headers).containsEntry("X-Request-Example", "ValueA");
			});
	}

	@Test
	public void addRequestHeaderFilterWorksMultipleValues() {
		testClient.get()
			.uri("/multivalueheaders")
			.header("Host", "www.addrequestheader.org")
			.exchange()
			.expectBody(Map.class)
			.consumeWith(result -> {
				Map<String, Object> headers = getMap(result.getResponseBody(), "headers");
				assertThat(headers).containsEntry("X-Request-Example", Arrays.asList("ValueA", "ValueB"));
			});
	}

	@Test
	public void addRequestHeaderFilterWorksJavaDsl() {
		testClient.get()
			.uri("/headers")
			.header("Host", "www.addrequestheaderjava.org")
			.exchange()
			.expectBody(Map.class)
			.consumeWith(result -> {
				Map<String, Object> headers = getMap(result.getResponseBody(), "headers");
				assertThat(headers).containsEntry("X-Request-Acme", "ValueB-www");
			});
	}

	@Test
	public void addRequestHeaderFilterMultipleValuesWorksJavaDsl() {
		testClient.get()
			.uri("/multivalueheaders")
			.header("Host", "www.addrequestheaderjava.org")
			.exchange()
			.expectBody(Map.class)
			.consumeWith(result -> {
				Map<String, Object> headers = getMap(result.getResponseBody(), "headers");
				assertThat(headers).containsEntry("X-Request-Acme", Arrays.asList("ValueB-www", "ValueC-www"));
			});
	}

	@Test
	public void toStringFormat() {
		NameValueConfig config = new NameValueConfig().setName("myname").setValue("myvalue");
		GatewayFilter filter = new AddRequestHeaderGatewayFilterFactory().apply(config);
		assertThat(filter.toString()).contains("myname").contains("myvalue");
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
				.route("add_request_header_java_test",
						r -> r.path("/headers")
							.and()
							.host("{sub}.addrequestheaderjava.org")
							.filters(f -> f.prefixPath("/httpbin").addRequestHeader("X-Request-Acme", "ValueB-{sub}"))
							.uri(uri))
				.route("add_multiple_request_header_java_test",
						r -> r.path("/multivalueheaders")
							.and()
							.host("{sub}.addrequestheaderjava.org")
							.filters(f -> f.prefixPath("/httpbin")
								.addRequestHeader("X-Request-Acme", "ValueB-{sub}")
								.addRequestHeader("X-Request-Acme", "ValueC-{sub}"))
							.uri(uri))
				.build();
		}

	}

}
