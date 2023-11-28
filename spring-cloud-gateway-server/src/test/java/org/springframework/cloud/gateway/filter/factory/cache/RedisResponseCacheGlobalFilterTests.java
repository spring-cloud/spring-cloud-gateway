/*
 * Copyright 2023-2023 the original author or authors.
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

package org.springframework.cloud.gateway.filter.factory.cache;

import java.util.UUID;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * @author Jesse Estum
 */
@DirtiesContext
@ActiveProfiles(profiles = "redis-cache-filter")
public class RedisResponseCacheGlobalFilterTests {

	private static final String CUSTOM_HEADER = "X-Custom-Date";

	@Nested
	@SpringBootTest(
			properties = { "spring.cloud.gateway.filter.redis-response-cache.enabled=true",
					"spring.cloud.gateway.global-filter.redis-response-cache.enabled=false" },
			webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
	public class GlobalCacheNotEnabled extends BaseWebClientTests {

		@Test
		void shouldNotCacheResponseWhenGlobalIsNotEnabled() {
			String uri = "/" + UUID.randomUUID() + "/global-cache-deactivated/headers";

			testClient.get().uri(uri).header("Host", "www.localresponsecache.org").header(CUSTOM_HEADER, "1").exchange()
					.expectBody().jsonPath("$.headers." + CUSTOM_HEADER);

			testClient.get().uri(uri).header("Host", "www.localresponsecache.org").header(CUSTOM_HEADER, "2").exchange()
					.expectBody().jsonPath("$.headers." + CUSTOM_HEADER).isEqualTo("2");
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
						.route("global_local_response_cache_deactivated_java_test",
								r -> r.path("/{namespace}/global-cache-deactivated/**").and()
										.host("{sub}.localresponsecache.org")
										.filters(f -> f.stripPrefix(2).prefixPath("/httpbin")).uri(uri))
						.build();
			}

		}

	}

	@Nested
	@SpringBootTest(properties = { "spring.cloud.gateway.filter.redis-response-cache.enabled=true" },
			webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
	@Testcontainers
	public class GlobalCacheEnabled extends BaseWebClientTests {

		@Container
		public static GenericContainer redis = new GenericContainer<>("redis:5.0.14-alpine").withExposedPorts(6379);

		@BeforeAll
		public static void startRedisContainer() {
			redis.start();
		}

		@DynamicPropertySource
		static void containerProperties(DynamicPropertyRegistry registry) {
			registry.add("spring.data.redis.host", redis::getHost);
			registry.add("spring.data.redis.port", redis::getFirstMappedPort);
		}

		@Test
		void shouldGlobalCacheResponseWhenRouteDoesNotHaveFilter() {
			String uri = "/" + UUID.randomUUID() + "/global-cache/headers";

			testClient.get().uri(uri).header("Host", "www.localresponsecache.org").header(CUSTOM_HEADER, "1").exchange()
					.expectBody().jsonPath("$.headers." + CUSTOM_HEADER);

			testClient.get().uri(uri).header("Host", "www.localresponsecache.org").header(CUSTOM_HEADER, "2").exchange()
					.expectBody().jsonPath("$.headers." + CUSTOM_HEADER).isEqualTo("1");
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
						.route("global_local_response_cache_java_test",
								r -> r.path("/{namespace}/global-cache/**").and().host("{sub}.localresponsecache.org")
										.filters(f -> f.stripPrefix(2).prefixPath("/httpbin")).uri(uri))
						.build();
			}

		}

	}

}
