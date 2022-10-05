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
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;
import static org.springframework.cloud.gateway.test.TestUtils.getMap;

/**
 * @author Marta Medio
 */
@SpringBootTest(webEnvironment = RANDOM_PORT)
@DirtiesContext
public class RemoveJsonAttributesResponseBodyGatewayFilterFactoryTests extends BaseWebClientTests {

	@Test
	public void removeJsonAttributeRootWorks() {
		testClient.post().uri("/post").header("Host", "www.removejsonattributes.org")
				.header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE).header("foo", "test")
				.header("bar", "test").exchange().expectStatus().isOk().expectBody(Map.class).consumeWith(result -> {
					Map<?, ?> response = result.getResponseBody();
					assertThat(response).isNotNull();

					String responseBody = (String) response.get("data");
					assertThat(responseBody).isNull();

					Map<String, Object> headers = getMap(response, "headers");
					assertThat(headers).containsKey("user-agent");

				});
	}

	@Test
	public void removeJsonAttributeRecursivelyWorks() {

		testClient.post().uri("/post").header("Host", "www.removejsonattributesrecursively.org")
				.header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE).header("foo", "test")
				.header("bar", "test").exchange().expectStatus().isOk().expectBody(Map.class).consumeWith(result -> {
					Map<?, ?> response = result.getResponseBody();
					assertThat(response).isNotNull();

					Map<String, Object> headers = getMap(response, "headers");
					assertThat(headers).doesNotContainKey("foo");
					assertThat(headers).containsEntry("bar", "test");
				});
	}

	@Test
	public void removeJsonAttributeNoMatchesWorks() {

		testClient.post().uri("/post").header("Host", "www.removejsonattributesnomatches.org")
				.header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE).exchange().expectStatus().isOk()
				.expectBody(Map.class).consumeWith(result -> {
					Map<?, ?> response = result.getResponseBody();
					assertThat(response).isNotNull();

					Map<String, Object> headers = getMap(response, "headers");
					assertThat(headers).isNotNull();
					assertThat(headers).containsEntry(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
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
			return builder.routes()
					.route("remove_json_attributes_root_level_java_test",
							r -> r.path("/post").and().host("{sub}.removejsonattributes.org")
									.filters(f -> f.removeJsonAttributes(false, "data", "foo")).uri(uri))
					.route("remove_json_attributes_recursively_java_test",
							r -> r.path("/post").and().host("{sub}.removejsonattributesrecursively.org")
									.filters(f -> f.removeJsonAttributes(true, "foo")).uri(uri))
					.route("remove_json_attributes_no_matches_java_test",
							r -> r.path("/post").and().host("{sub}.removejsonattributesnomatches.org")
									.filters(f -> f.removeJsonAttributes("test")).uri(uri))
					.build();
		}

	}

}
