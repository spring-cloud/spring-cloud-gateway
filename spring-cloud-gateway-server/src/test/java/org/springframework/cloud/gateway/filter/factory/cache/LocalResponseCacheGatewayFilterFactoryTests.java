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

package org.springframework.cloud.gateway.filter.factory.cache;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.util.StringUtils;
import org.springframework.util.unit.DataSize;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Offset.offset;

/**
 * @author Ignacio Lozano
 * @author Marta Medio
 */
@DirtiesContext
@ActiveProfiles(profiles = "local-cache-filter")
public class LocalResponseCacheGatewayFilterFactoryTests extends BaseWebClientTests {

	private static final String CUSTOM_HEADER = "X-Custom-Header";

	private static Long parseMaxAge(String cacheControlValue) {
		if (StringUtils.hasText(cacheControlValue)) {
			Pattern maxAgePattern = Pattern.compile("\\bmax-age=(\\d+)\\b");
			Matcher matcher = maxAgePattern.matcher(cacheControlValue);
			if (matcher.find()) {
				return Long.parseLong(matcher.group(1));
			}
		}
		return null;
	}

	@Nested
	@SpringBootTest(properties = { "spring.cloud.gateway.filter.local-response-cache.enabled=true" },
			webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
	public class UsingFilterParams extends BaseWebClientTests {

		@Test
		void shouldNotCacheResponseWhenGetRequestHasBody() {
			String uri = "/" + UUID.randomUUID() + "/cache/headers";

			testClient.method(HttpMethod.GET)
				.uri(uri)
				.header("Host", "www.localresponsecache.org")
				.header(CUSTOM_HEADER, "1")
				.bodyValue("whatever")
				.exchange()
				.expectBody()
				.jsonPath("$.headers." + CUSTOM_HEADER);

			testClient.method(HttpMethod.GET)
				.uri(uri)
				.header("Host", "www.localresponsecache.org")
				.bodyValue("whatever")
				.header(CUSTOM_HEADER, "2")
				.exchange()
				.expectBody()
				.jsonPath("$.headers." + CUSTOM_HEADER)
				.isEqualTo("2");
		}

		@Test
		void shouldNotCacheResponseWhenPostRequestHasBody() {
			String uri = "/" + UUID.randomUUID() + "/cache/headers";

			testClient.method(HttpMethod.POST)
				.uri(uri)
				.header("Host", "www.localresponsecache.org")
				.header(CUSTOM_HEADER, "1")
				.bodyValue("whatever")
				.exchange()
				.expectBody()
				.jsonPath("$.headers." + CUSTOM_HEADER);

			testClient.method(HttpMethod.POST)
				.uri(uri)
				.header("Host", "www.localresponsecache.org")
				.bodyValue("whatever")
				.header(CUSTOM_HEADER, "2")
				.exchange()
				.expectBody()
				.jsonPath("$.headers." + CUSTOM_HEADER)
				.isEqualTo("2");
		}

		@Test
		void shouldNotCacheWhenCacheControlAsksToDoNotCache() {
			String uri = "/" + UUID.randomUUID() + "/cache/headers";

			testClient.get()
				.uri(uri)
				.header("Host", "www.localresponsecache.org")
				.header(CUSTOM_HEADER, "1")
				.exchange()
				.expectBody()
				.jsonPath("$.headers." + CUSTOM_HEADER);

			testClient.get()
				.uri(uri)
				.header("Host", "www.localresponsecache.org")
				.header(CUSTOM_HEADER, "2")
				// Cache-Control asks to not use the cached content and not store the
				// response
				.header(HttpHeaders.CACHE_CONTROL, CacheControl.noStore().getHeaderValue())
				.exchange()
				.expectBody()
				.jsonPath("$.headers." + CUSTOM_HEADER)
				.isEqualTo("2");
		}

		@Test
		void shouldNotIncludeMustRevalidateNoStoreAndNoCacheDirectivesWhenMaxAgeIsPositive() {
			String uri = "/" + UUID.randomUUID() + "/cache/headers";

			var response = testClient.get()
				.uri(uri)
				.header("Host", "www.localresponsecache.org")
				.exchange()
				.expectBody()
				.returnResult();
			var maxAge = response.getResponseHeaders()
				.get(HttpHeaders.CACHE_CONTROL)
				.stream()
				.map(LocalResponseCacheGatewayFilterFactoryTests::parseMaxAge)
				.filter(Objects::nonNull)
				.findAny()
				.orElse(null);

			assertThat(maxAge).isGreaterThan(0L);
			assertThat(response.getResponseHeaders().get(HttpHeaders.CACHE_CONTROL)).doesNotContain("no-store",
					"must-revalidate", "no-cache");
		}

		@Test
		void shouldCacheResponseWhenOnlyNonVaryHeaderIsDifferent() {
			String uri = "/" + UUID.randomUUID() + "/cache/headers";

			testClient.get()
				.uri(uri)
				.header("Host", "www.localresponsecache.org")
				.header(CUSTOM_HEADER, "1")
				.exchange()
				.expectBody()
				.jsonPath("$.headers." + CUSTOM_HEADER)
				.value(customHeaderFromReq1 -> testClient.get()
					.uri(uri)
					.header("Host", "www.localresponsecache.org")
					.header(CUSTOM_HEADER, "2")
					.exchange()
					.expectBody()
					.jsonPath("$.headers." + CUSTOM_HEADER, customHeaderFromReq1));
		}

		@Test
		void shouldNotCacheResponseWhenVaryHeaderIsDifferent() {
			String varyHeader = HttpHeaders.ORIGIN;
			String sameUri = "/" + UUID.randomUUID() + "/cache/vary-on-header";
			String firstNonVary = "1";
			String secondNonVary = "2";
			assertNonVaryHeaderInContent(sameUri, varyHeader, "origin-1", CUSTOM_HEADER, firstNonVary, firstNonVary);
			assertNonVaryHeaderInContent(sameUri, varyHeader, "origin-1", CUSTOM_HEADER, secondNonVary, firstNonVary);
			assertNonVaryHeaderInContent(sameUri, varyHeader, "origin-2", CUSTOM_HEADER, secondNonVary, secondNonVary);
		}

		@Test
		void shouldNotCacheResponseWhenResponseVaryIsWildcard() {
			String uri = "/" + UUID.randomUUID() + "/cache/vary-on-header";
			// Vary: *
			testClient.get()
				.uri(uri)
				.header("Host", "www.localresponsecache.org")
				.header(CUSTOM_HEADER, "1")
				.header("X-Request-Vary", "*")
				.exchange()
				.expectBody()
				.jsonPath("$.headers." + CUSTOM_HEADER, "1");
			testClient.get()
				.uri(uri)
				.header("Host", "www.localresponsecache.org")
				.header(CUSTOM_HEADER, "2")
				.header("X-Request-Vary", "*")
				.exchange()
				.expectBody()
				.jsonPath("$.headers." + CUSTOM_HEADER, "2");
		}

		@Test
		void shouldNotCacheResponseWhenPathIsDifferent() {
			String uri = "/" + UUID.randomUUID() + "/cache/headers";
			String uri2 = "/" + UUID.randomUUID() + "/cache/headers";

			testClient.get()
				.uri(uri)
				.header("Host", "www.localresponsecache.org")
				.header(CUSTOM_HEADER, "1")
				.exchange()
				.expectBody()
				.jsonPath("$.headers." + CUSTOM_HEADER);

			testClient.get()
				.uri(uri2)
				.header("Host", "www.localresponsecache.org")
				.header(CUSTOM_HEADER, "2")
				.exchange()
				.expectBody()
				.jsonPath("$.headers." + CUSTOM_HEADER)
				.isEqualTo("2");
		}

		@Test
		void shouldDecreaseCacheControlMaxAgeTimeWhenResponseIsFromCache() throws InterruptedException {
			String uri = "/" + UUID.randomUUID() + "/cache/headers";
			Long maxAgeRequest1 = testClient.get()
				.uri(uri)
				.header("Host", "www.localresponsecache.org")
				.exchange()
				.expectBody()
				.returnResult()
				.getResponseHeaders()
				.get(HttpHeaders.CACHE_CONTROL)
				.stream()
				.map(LocalResponseCacheGatewayFilterFactoryTests::parseMaxAge)
				.filter(Objects::nonNull)
				.findAny()
				.orElse(null);
			Thread.sleep(2000);
			Long maxAgeRequest2 = testClient.get()
				.uri(uri)
				.header("Host", "www.localresponsecache.org")
				.exchange()
				.expectBody()
				.returnResult()
				.getResponseHeaders()
				.get(HttpHeaders.CACHE_CONTROL)
				.stream()
				.map(LocalResponseCacheGatewayFilterFactoryTests::parseMaxAge)
				.filter(Objects::nonNull)
				.findAny()
				.orElse(null);

			assertThat(maxAgeRequest2).isLessThan(maxAgeRequest1);
		}

		@Test
		void shouldNotCacheResponseWhenTimeToLiveIsReached() {
			String uri = "/" + UUID.randomUUID() + "/ephemeral-cache/headers";
			testClient.get()
				.uri(uri)
				.header("Host", "www.localresponsecache.org")
				.header(CUSTOM_HEADER, "1")
				.exchange()
				.expectBody()
				.jsonPath("$.headers." + CUSTOM_HEADER)
				.value(customHeaderFromReq1 -> {
					try {
						Thread.sleep(100); // Min time to have entry expired
						testClient.get()
							.uri(uri)
							.header("Host", "www.localresponsecache.org")
							.header(CUSTOM_HEADER, "2")
							.exchange()
							.expectBody()
							.jsonPath("$.headers." + CUSTOM_HEADER)
							.isEqualTo("2");
					}
					catch (InterruptedException e) {
						throw new RuntimeException(e);
					}
				});
		}

		@Test
		void shouldNotCacheWhenLocalResponseCacheSizeIsReached() {
			String uri = "/" + UUID.randomUUID() + "/one-byte-cache/headers";

			testClient.get()
				.uri(uri)
				.header("Host", "www.localresponsecache.org")
				.header(CUSTOM_HEADER, "1")
				.exchange()
				.expectBody()
				.jsonPath("$.headers." + CUSTOM_HEADER);

			testClient.get()
				.uri(uri)
				.header("Host", "www.localresponsecache.org")
				.header(CUSTOM_HEADER, "2")
				.exchange()
				.expectBody()
				.jsonPath("$.headers." + CUSTOM_HEADER, "2");
		}

		@Test
		void shouldNotCacheWhenAuthorizationHeaderIsDifferent() {
			String uri = "/" + UUID.randomUUID() + "/cache/headers";

			testClient.get()
				.uri(uri)
				.header("Host", "www.localresponsecache.org")
				.header(HttpHeaders.AUTHORIZATION, "1")
				.header(CUSTOM_HEADER, "1")
				.exchange()
				.expectBody()
				.jsonPath("$.headers." + CUSTOM_HEADER);

			testClient.get()
				.uri(uri)
				.header("Host", "www.localresponsecache.org")
				.header(HttpHeaders.AUTHORIZATION, "2")
				.header(CUSTOM_HEADER, "2")
				.exchange()
				.expectBody()
				.jsonPath("$.headers." + CUSTOM_HEADER, "2");
		}

		@Test
		void shouldNotReturnPragmaHeaderInNonCachedAndCachedResponses() {
			shouldNotReturnHeader(HttpHeaders.PRAGMA);
		}

		@Test
		void shouldNotReturnExpiresHeaderInNonCachedAndCachedResponses() {
			shouldNotReturnHeader(HttpHeaders.EXPIRES);
		}

		private void shouldNotReturnHeader(String header) {
			String uri = "/" + UUID.randomUUID() + "/cache/headers";

			testClient.get()
				.uri(uri)
				.header("Host", "www.localresponsecache.org")
				.exchange()
				.expectHeader()
				.doesNotExist(header);

			testClient.get()
				.uri(uri)
				.header("Host", "www.localresponsecache.org")
				.exchange()
				.expectHeader()
				.doesNotExist(header);
		}

		void assertNonVaryHeaderInContent(String uri, String varyHeader, String varyHeaderValue, String nonVaryHeader,
				String nonVaryHeaderValue, String expectedNonVaryResponse) {
			testClient.get()
				.uri(uri)
				.header("Host", "www.localresponsecache.org")
				.header("X-Request-Vary", varyHeader)
				.header(varyHeader, varyHeaderValue)
				.header(nonVaryHeader, nonVaryHeaderValue)
				.exchange()
				.expectBody(Map.class)
				.consumeWith(response -> {
					assertThat(response.getResponseHeaders()).hasEntrySatisfying("Vary",
							o -> assertThat(o).contains(varyHeader));
					assertThat((Map) response.getResponseBody().get("headers")).containsEntry(nonVaryHeader,
							expectedNonVaryResponse);
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
					.route("local_response_cache_java_test",
							r -> r.path("/{namespace}/cache/**")
								.and()
								.host("{sub}.localresponsecache.org")
								.filters(f -> f.stripPrefix(2)
									.prefixPath("/httpbin")
									.localResponseCache(Duration.ofMinutes(2), null))
								.uri(uri))
					.route("100_millisec_ephemeral_prefix_local_response_cache_java_test",
							r -> r.path("/{namespace}/ephemeral-cache/**")
								.and()
								.host("{sub}.localresponsecache.org")
								.filters(f -> f.stripPrefix(2)
									.prefixPath("/httpbin")
									.localResponseCache(Duration.ofMillis(100), null))
								.uri(uri))
					.route("min_sized_prefix_local_response_cache_java_test",
							r -> r.path("/{namespace}/one-byte-cache/**")
								.and()
								.host("{sub}.localresponsecache.org")
								.filters(f -> f.stripPrefix(2)
									.prefixPath("/httpbin")
									.localResponseCache(null, DataSize.ofBytes(1L)))
								.uri(uri))
					.build();
			}

		}

	}

	@Nested
	@SpringBootTest(
			properties = { "spring.cloud.gateway.filter.local-response-cache.enabled=true",
					"spring.cloud.gateway.filter.local-response-cache.timeToLive=20s" },
			webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
	public class UsingPropertiesAsDefault extends BaseWebClientTests {

		@Test
		void shouldApplyMaxAgeFromPropertiesWhenFilterHasNoParams() throws InterruptedException {
			String uri = "/" + UUID.randomUUID() + "/cache/headers";
			Long maxAgeRequest1 = testClient.get()
				.uri(uri)
				.header("Host", "www.localresponsecache.org")
				.exchange()
				.expectBody()
				.returnResult()
				.getResponseHeaders()
				.get(HttpHeaders.CACHE_CONTROL)
				.stream()
				.map(LocalResponseCacheGatewayFilterFactoryTests::parseMaxAge)
				.filter(Objects::nonNull)
				.findAny()
				.orElse(null);
			assertThat(maxAgeRequest1).isLessThanOrEqualTo(20L);

			Thread.sleep(2000);

			Long maxAgeRequest2 = testClient.get()
				.uri(uri)
				.header("Host", "www.localresponsecache.org")
				.exchange()
				.expectBody()
				.returnResult()
				.getResponseHeaders()
				.get(HttpHeaders.CACHE_CONTROL)
				.stream()
				.map(LocalResponseCacheGatewayFilterFactoryTests::parseMaxAge)
				.filter(Objects::nonNull)
				.findAny()
				.orElse(null);

			assertThat(maxAgeRequest2).isLessThan(maxAgeRequest1);
		}

		@Test
		void shouldNotCacheWhenPrivateDirectiveIsInRequest() {
			testClient = testClient.mutate().responseTimeout(Duration.ofHours(1)).build();

			String uri = "/" + UUID.randomUUID() + "/cache/headers";

			testClient.get()
				.uri(uri)
				.header("Host", "www.localresponsecache.org")
				.header(HttpHeaders.CACHE_CONTROL, CacheControl.noStore().getHeaderValue())
				.header(CUSTOM_HEADER, "1")
				.exchange()
				.expectBody()
				.jsonPath("$.headers." + CUSTOM_HEADER);

			testClient.get()
				.uri(uri)
				.header("Host", "www.localresponsecache.org")
				.header(CUSTOM_HEADER, "2")
				.exchange()
				.expectBody()
				.jsonPath("$.headers." + CUSTOM_HEADER)
				.isEqualTo("2");

			testClient.get()
				.uri(uri)
				.header("Host", "www.localresponsecache.org")
				.header(CUSTOM_HEADER, "3") // second
											// request
											// cached
											// "2"
											// ->
											// "3"
											// will
											// be
											// ignored
				.exchange()
				.expectBody()
				.jsonPath("$.headers." + CUSTOM_HEADER)
				.isEqualTo("2");
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
					.route("local_response_cache_java_test",
							r -> r.path("/{namespace}/cache/**")
								.and()
								.host("{sub}.localresponsecache.org")
								.filters(f -> f.stripPrefix(2).prefixPath("/httpbin").localResponseCache(null, null))
								.uri(uri))
					.build();
			}

		}

	}

	@Nested
	@SpringBootTest(properties = { "spring.cloud.gateway.filter.local-response-cache.enabled=true",
			"spring.cloud.gateway.filter.local-response-cache.time-to-live=2m",
			"spring.cloud.gateway.filter.local-response-cache.request.no-cache-strategy=skip-update-cache-entry" },
			webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
	public class DirectiveNoCacheSkippingUpdate extends BaseWebClientTests {

		@Test
		void shouldNotCacheWhenCacheControlAsksToValidateWithNotCache_refreshCacheWhenDirectiveNoCache()
				throws InterruptedException {
			String uri = "/" + UUID.randomUUID() + "/cache/headers";

			// 1. Store in cache - max-age ~= 2m AND NOT
			// (must-revalidate,no-cache,no-store)
			final Instant when1stRequest = Instant.now();
			var firstResponse = testClient.get()
				.uri(uri)
				.header("Host", "www.localresponsecache.org")
				.header(CUSTOM_HEADER, "1")
				.exchange()
				.expectBody()
				.jsonPath("$.headers." + CUSTOM_HEADER)
				.isEqualTo("1")
				.returnResult();
			var maxAge1st = firstResponse.getResponseHeaders()
				.get(HttpHeaders.CACHE_CONTROL)
				.stream()
				.map(LocalResponseCacheGatewayFilterFactoryTests::parseMaxAge)
				.filter(Objects::nonNull)
				.findAny()
				.orElse(null);
			assertThat(maxAge1st).isCloseTo(Duration.ofMinutes(2).toSeconds(), offset(10L));
			assertThat(firstResponse.getResponseHeaders().getCacheControl()).doesNotContain("must-revalidate",
					"no-cache", "no-store");

			// 2. "no-cache" should return max-age=0 & must-revalidate,no-cache,no-store
			var secondResponse = testClient.get()
				.uri(uri)
				.header("Host", "www.localresponsecache.org")
				.header(CUSTOM_HEADER, "2")
				// Cache-Control asks to not use the cached content
				.header(HttpHeaders.CACHE_CONTROL, CacheControl.noCache().getHeaderValue())
				.exchange()
				.expectBody()
				.jsonPath("$.headers." + CUSTOM_HEADER)
				.isEqualTo("2")
				.returnResult();
			var maxAge2nd = secondResponse.getResponseHeaders()
				.get(HttpHeaders.CACHE_CONTROL)
				.stream()
				.map(LocalResponseCacheGatewayFilterFactoryTests::parseMaxAge)
				.filter(Objects::nonNull)
				.findAny()
				.orElse(null);
			assertThat(maxAge2nd).isZero();

			// 3. After 2s, max-age = (when1stRequest) - 1s - offset_delay
			var waitDuration = Duration.ofSeconds(1);
			Thread.sleep(waitDuration.toMillis()); // Wait 2s to check max-age renewed
			final Instant when3rdRequest = Instant.now();
			var thirdResponseCached = testClient.get()
				.uri(uri)
				.header("Host", "www.localresponsecache.org")
				.header(CUSTOM_HEADER, "3")
				.exchange()
				.expectBody()
				.jsonPath("$.headers." + CUSTOM_HEADER)
				.isEqualTo("1")
				.returnResult();
			var maxAge3rd = thirdResponseCached.getResponseHeaders()
				.get(HttpHeaders.CACHE_CONTROL)
				.stream()
				.map(LocalResponseCacheGatewayFilterFactoryTests::parseMaxAge)
				.filter(Objects::nonNull)
				.findAny()
				.orElse(null);
			assertThat(maxAge3rd).isCloseTo(
					Duration.ofMinutes(2).minus(Duration.between(when1stRequest, when3rdRequest)).getSeconds(),
					offset(10L));
			assertThat(maxAge3rd).isNotZero();
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
					.route("local_response_cache_java_test",
							r -> r.path("/{namespace}/cache/**")
								.and()
								.host("{sub}.localresponsecache.org")
								.filters(f -> f.stripPrefix(2).prefixPath("/httpbin").localResponseCache(null, null))
								.uri(uri))
					.build();
			}

		}

	}

	@Nested
	@SpringBootTest(
			properties = { "spring.cloud.gateway.filter.local-response-cache.enabled=true",
					"spring.cloud.gateway.filter.local-response-cache.time-to-live=2m",
					"spring.cloud.gateway.filter.local-response-cache.request.no-cache-strategy=update-cache-entry" },
			webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
	public class DirectiveNoCacheWithUpdate extends BaseWebClientTests {

		@Test
		void oldMaxAgeWhenNoCacheRequest() throws InterruptedException {
			testClient = testClient.mutate().responseTimeout(Duration.ofHours(1)).build();

			String uri = "/" + UUID.randomUUID() + "/cache/headers";
			// First request -> cache miss
			final Instant when1stRequest = Instant.now();
			Long maxAgeRequest1 = testClient.get()
				.uri(uri)
				.header("Host", "www.localresponsecache.org")
				.exchange()
				.expectBody()
				.returnResult()
				.getResponseHeaders()
				.get(HttpHeaders.CACHE_CONTROL)
				.stream()
				.map(LocalResponseCacheGatewayFilterFactoryTests::parseMaxAge)
				.filter(Objects::nonNull)
				.findAny()
				.orElse(null);
			assertThat(maxAgeRequest1).isCloseTo(Duration.ofMinutes(2).toSeconds(), offset(10L));

			// Second request + no-cache -> skip cache and ignore update
			Thread.sleep(1000);
			final Duration between1stAnd2ndRequest = Duration.between(when1stRequest, Instant.now());
			Long maxAgeRequest2 = testClient.get()
				.uri(uri)
				.header("Host", "www.localresponsecache.org")
				.header(HttpHeaders.CACHE_CONTROL, CacheControl.noCache().getHeaderValue())
				.exchange()
				.expectBody()
				.returnResult()
				.getResponseHeaders()
				.get(HttpHeaders.CACHE_CONTROL)
				.stream()
				.map(LocalResponseCacheGatewayFilterFactoryTests::parseMaxAge)
				.filter(Objects::nonNull)
				.findAny()
				.orElse(null);
			assertThat(maxAgeRequest2).isCloseTo(Duration.ofMinutes(2).minus(between1stAnd2ndRequest).getSeconds(),
					offset(10L));

			// Third request -> cache hit -> entry (and max-age) is updated
			Thread.sleep(1000);
			Long maxAgeRequest3 = testClient.get()
				.uri(uri)
				.header("Host", "www.localresponsecache.org")
				.exchange()
				.expectBody()
				.returnResult()
				.getResponseHeaders()
				.get(HttpHeaders.CACHE_CONTROL)
				.stream()
				.map(LocalResponseCacheGatewayFilterFactoryTests::parseMaxAge)
				.filter(Objects::nonNull)
				.findAny()
				.orElse(null);
			assertThat(maxAgeRequest3).isCloseTo(Duration.ofMinutes(2).toSeconds(), offset(10L));
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
					.route("local_response_cache_java_test",
							r -> r.path("/{namespace}/cache/**")
								.and()
								.host("{sub}.localresponsecache.org")
								.filters(f -> f.stripPrefix(2).prefixPath("/httpbin").localResponseCache(null, null))
								.uri(uri))
					.build();
			}

		}

	}

}
