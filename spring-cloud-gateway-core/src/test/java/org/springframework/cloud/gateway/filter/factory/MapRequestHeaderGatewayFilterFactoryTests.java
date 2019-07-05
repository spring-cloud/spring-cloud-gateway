/*
 * Copyright 2013-2019 the original author or authors.
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

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.Test;
import org.junit.runner.RunWith;

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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;
import static org.springframework.cloud.gateway.test.TestUtils.getMap;
import static org.springframework.cloud.gateway.test.TestUtils.getMultiValuedHeader;

/**
 * @author Tony Clarke
 */
@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = RANDOM_PORT)
@DirtiesContext
@ActiveProfiles(profiles = "request-map-header-web-filter")
public class MapRequestHeaderGatewayFilterFactoryTests extends BaseWebClientTests {

	@Test
	public void mapRequestHeaderFilterWorks() {
		testClient.get().uri("/headers").header("Host", "www.addrequestheader.org").header("a", "tome")
				.exchange().expectBody(Map.class).consumeWith(result -> {
					Map<String, Object> headers = getMap(result.getResponseBody(),
							"headers");
					assertThat(headers).containsEntry("X-Request-Example", "tome");
				});
	}
	
	@SuppressWarnings("unchecked")
	@Test
	public void mapRequestHeaderWithMultiValueFilterWorks() {
		testClient.get().uri("/multivalueheaders").header("Host", "www.addrequestheader.org").header("a", "tome", "toyou")
				.exchange().expectBody(Set.class).consumeWith(result -> {
					List<String> multiValuedHeader = getMultiValuedHeader(result.getResponseBody(), "X-Request-Example");
					assertThat(multiValuedHeader).contains("tome", "toyou");
				});
	}
	
	@Test
	public void mapRequestHeaderWithNullValueFilterWorks() {
		testClient.get().uri("/headers").header("Host", "www.addrequestheader.org").header("a", (String)null)
				.exchange().expectBody(Map.class).consumeWith(result -> {
					Map<String, Object> headers = getMap(result.getResponseBody(),
							"headers");
					assertThat(headers).doesNotContainKey("X-Request-Example");
				});
	}
	
	@Test
	public void mapRequestHeaderWhenInputHeaderDoesNotExist() {
		testClient.get().uri("/headers").header("Host", "www.addrequestheader.org")
				.exchange().expectBody(Map.class).consumeWith(result -> {
					Map<String, Object> headers = getMap(result.getResponseBody(),
							"headers");
					assertThat(headers).doesNotContainKey("X-Request-Example");
				});
	}


	@EnableAutoConfiguration
	@SpringBootConfiguration
	@Import(DefaultTestConfig.class)
	public static class TestConfig {

		@Value("${test.uri}")
		String uri;

		@Bean
		public RouteLocator testRouteLocator(RouteLocatorBuilder builder) {
			return builder.routes().route("add_request_header_java_test",
					r -> r.path("/headers").and().host("**.addrequestheaderjava.org")
							.filters(f -> f.prefixPath("/httpbin")
									.addRequestHeader("X-Request-Acme", "ValueB"))
							.uri(uri))
					.build();
		}

	}

}
