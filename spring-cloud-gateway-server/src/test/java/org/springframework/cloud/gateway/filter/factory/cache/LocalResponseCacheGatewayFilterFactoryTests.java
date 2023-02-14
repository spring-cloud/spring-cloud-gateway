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
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

/**
 * @author Ignacio Lozano
 * @author Marta Medio
 */
@SpringBootTest(properties = { "spring.cloud.gateway.filter.local-response-cache.enabled=true" },
		webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DirtiesContext
@ActiveProfiles(profiles = "local-cache-filter")
public class LocalResponseCacheGatewayFilterFactoryTests extends BaseWebClientTests {

	private static final String CUSTOM_HEADER = "X-Custom-Date";

	@Test
	void shouldGlobalCacheResponseWhenRouteDoesNotHaveFilter() {
		String uri = "/" + UUID.randomUUID() + "/global-cache/headers";

		testClient.get().uri(uri).header("Host", "www.localresponsecache.org").header(CUSTOM_HEADER, "1").exchange()
				.expectBody().jsonPath("$.headers." + CUSTOM_HEADER);

		testClient.get().uri(uri).header("Host", "www.localresponsecache.org").header(CUSTOM_HEADER, "2").exchange()
				.expectBody().jsonPath("$.headers." + CUSTOM_HEADER).isEqualTo("1");
	}

	@Test
	void shouldNotCacheResponseWhenGetRequestHasBody() {
		String uri = "/" + UUID.randomUUID() + "/cache/headers";

		testClient.method(HttpMethod.GET).uri(uri).header("Host", "www.localresponsecache.org")
				.header(CUSTOM_HEADER, "1").bodyValue("whatever").exchange().expectBody()
				.jsonPath("$.headers." + CUSTOM_HEADER);

		testClient.method(HttpMethod.GET).uri(uri).header("Host", "www.localresponsecache.org").bodyValue("whatever")
				.header(CUSTOM_HEADER, "2").exchange().expectBody().jsonPath("$.headers." + CUSTOM_HEADER)
				.isEqualTo("2");
	}

	@Test
	void shouldNotCacheResponseWhenPostRequestHasBody() {
		String uri = "/" + UUID.randomUUID() + "/cache/headers";

		testClient.method(HttpMethod.POST).uri(uri).header("Host", "www.localresponsecache.org")
				.header(CUSTOM_HEADER, "1").bodyValue("whatever").exchange().expectBody()
				.jsonPath("$.headers." + CUSTOM_HEADER);

		testClient.method(HttpMethod.POST).uri(uri).header("Host", "www.localresponsecache.org").bodyValue("whatever")
				.header(CUSTOM_HEADER, "2").exchange().expectBody().jsonPath("$.headers." + CUSTOM_HEADER)
				.isEqualTo("2");
	}

	@Test
	void shouldNotCacheWhenCacheControlAsksToDoNotCache() {
		String uri = "/" + UUID.randomUUID() + "/cache/headers";

		testClient.get().uri(uri).header("Host", "www.localresponsecache.org").header(CUSTOM_HEADER, "1").exchange()
				.expectBody().jsonPath("$.headers." + CUSTOM_HEADER);

		testClient.get().uri(uri).header("Host", "www.localresponsecache.org").header(CUSTOM_HEADER, "2")
				// Cache-Control asks to not use the cached content and not store the
				// response
				.header(HttpHeaders.CACHE_CONTROL, CacheControl.noStore().getHeaderValue()).exchange().expectBody()
				.jsonPath("$.headers." + CUSTOM_HEADER).isEqualTo("2");
	}

	@Test
	void shouldCacheAndReturnNotModifiedStatusWhenCacheControlIsNoCache() {
		String uri = "/" + UUID.randomUUID() + "/cache/headers";

		testClient.get().uri(uri).header("Host", "www.localresponsecache.org").header(CUSTOM_HEADER, "1").exchange()
				.expectBody().jsonPath("$.headers." + CUSTOM_HEADER);

		testClient.get().uri(uri).header("Host", "www.localresponsecache.org").header(CUSTOM_HEADER, "2")
				// Cache-Control asks to not return cached content because it is
				// HttpHeaders.NotModified
				.header(HttpHeaders.CACHE_CONTROL, CacheControl.noCache().getHeaderValue()).exchange().expectStatus()
				.isNotModified().expectBody().isEmpty();
	}

	@Test
	void shouldCacheResponseWhenOnlyNonVaryHeaderIsDifferent() {
		String uri = "/" + UUID.randomUUID() + "/cache/headers";

		testClient.get().uri(uri).header("Host", "www.localresponsecache.org").header(CUSTOM_HEADER, "1").exchange()
				.expectBody().jsonPath("$.headers." + CUSTOM_HEADER)
				.value(customHeaderFromReq1 -> testClient.get().uri(uri).header("Host", "www.localresponsecache.org")
						.header(CUSTOM_HEADER, "2").exchange().expectBody()
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
		testClient.get().uri(uri).header("Host", "www.localresponsecache.org").header(CUSTOM_HEADER, "1")
				.header("X-Request-Vary", "*").exchange().expectBody().jsonPath("$.headers." + CUSTOM_HEADER, "1");
		testClient.get().uri(uri).header("Host", "www.localresponsecache.org").header(CUSTOM_HEADER, "2")
				.header("X-Request-Vary", "*").exchange().expectBody().jsonPath("$.headers." + CUSTOM_HEADER, "2");
	}

	@Test
	void shouldNotCacheResponseWhenPathIsDifferent() {
		String uri = "/" + UUID.randomUUID() + "/cache/headers";
		String uri2 = "/" + UUID.randomUUID() + "/cache/headers";

		testClient.get().uri(uri).header("Host", "www.localresponsecache.org").header(CUSTOM_HEADER, "1").exchange()
				.expectBody().jsonPath("$.headers." + CUSTOM_HEADER);

		testClient.get().uri(uri2).header("Host", "www.localresponsecache.org").header(CUSTOM_HEADER, "2").exchange()
				.expectBody().jsonPath("$.headers." + CUSTOM_HEADER).isEqualTo("2");
	}

	@Test
	void shouldDecreaseCacheControlMaxAgeTimeWhenResponseIsFromCache() throws InterruptedException {
		String uri = "/" + UUID.randomUUID() + "/cache/headers";
		Long maxAgeRequest1 = testClient.get().uri(uri).header("Host", "www.localresponsecache.org").exchange()
				.expectBody().returnResult().getResponseHeaders().get(HttpHeaders.CACHE_CONTROL).stream()
				.map(this::parseMaxAge).filter(Objects::nonNull).findAny().orElse(null);
		Thread.sleep(2000);
		Long maxAgeRequest2 = testClient.get().uri(uri).header("Host", "www.localresponsecache.org").exchange()
				.expectBody().returnResult().getResponseHeaders().get(HttpHeaders.CACHE_CONTROL).stream()
				.map(this::parseMaxAge).filter(Objects::nonNull).findAny().orElse(null);

		assertThat(maxAgeRequest2).isLessThan(maxAgeRequest1);
	}

	@Test
	void shouldNotCacheResponseWhenTimeToLiveIsReached() {
		String uri = "/" + UUID.randomUUID() + "/ephemeral-cache/headers";
		testClient.get().uri(uri).header("Host", "www.localresponsecache.org").header(CUSTOM_HEADER, "1").exchange()
				.expectBody().jsonPath("$.headers." + CUSTOM_HEADER).value(customHeaderFromReq1 -> {
					try {
						Thread.sleep(100); // Min time to have entry expired
						testClient.get().uri(uri).header("Host", "www.localresponsecache.org")
								.header(CUSTOM_HEADER, "2").exchange().expectBody()
								.jsonPath("$.headers." + CUSTOM_HEADER).isEqualTo("2");
					}
					catch (InterruptedException e) {
						throw new RuntimeException(e);
					}
				});
	}

	@Test
	void shouldNotCacheWhenLocalResponseCacheSizeIsReached() {
		String uri = "/" + UUID.randomUUID() + "/one-byte-cache/headers";

		testClient.get().uri(uri).header("Host", "www.localresponsecache.org").header(CUSTOM_HEADER, "1").exchange()
				.expectBody().jsonPath("$.headers." + CUSTOM_HEADER);

		testClient.get().uri(uri).header("Host", "www.localresponsecache.org").header(CUSTOM_HEADER, "2").exchange()
				.expectBody().jsonPath("$.headers." + CUSTOM_HEADER, "2");
	}

	@Test
	void shouldNotCacheWhenAuthorizationHeaderIsDifferent() {
		String uri = "/" + UUID.randomUUID() + "/cache/headers";

		testClient.get().uri(uri).header("Host", "www.localresponsecache.org").header(HttpHeaders.AUTHORIZATION, "1")
				.header(CUSTOM_HEADER, "1").exchange().expectBody().jsonPath("$.headers." + CUSTOM_HEADER);

		testClient.get().uri(uri).header("Host", "www.localresponsecache.org").header(HttpHeaders.AUTHORIZATION, "2")
				.header(CUSTOM_HEADER, "2").exchange().expectBody().jsonPath("$.headers." + CUSTOM_HEADER, "2");
	}

	private Long parseMaxAge(String cacheControlValue) {
		if (StringUtils.hasText(cacheControlValue)) {
			Pattern maxAgePattern = Pattern.compile("\\bmax-age=(\\d+)\\b");
			Matcher matcher = maxAgePattern.matcher(cacheControlValue);
			if (matcher.find()) {
				return Long.parseLong(matcher.group(1));
			}
		}
		return null;
	}

	void assertNonVaryHeaderInContent(String uri, String varyHeader, String varyHeaderValue, String nonVaryHeader,
			String nonVaryHeaderValue, String expectedNonVaryResponse) {
		testClient.get().uri(uri).header("Host", "www.localresponsecache.org").header("X-Request-Vary", varyHeader)
				.header(varyHeader, varyHeaderValue).header(nonVaryHeader, nonVaryHeaderValue).exchange()
				.expectBody(Map.class).consumeWith(response -> {
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
					.route("global_local_response_cache_java_test",
							r -> r.path("/{namespace}/global-cache/**").and().host("{sub}.localresponsecache.org")
									.filters(f -> f.stripPrefix(2).prefixPath("/httpbin")).uri(uri))
					.route("local_response_cache_java_test",
							r -> r.path("/{namespace}/cache/**").and().host("{sub}.localresponsecache.org")
									.filters(f -> f.stripPrefix(2).prefixPath("/httpbin")
											.localResponseCache(Duration.ofMinutes(2), null))
									.uri(uri))
					.route("100_millisec_ephemeral_prefix_local_response_cache_java_test",
							r -> r.path("/{namespace}/ephemeral-cache/**").and().host("{sub}.localresponsecache.org")
									.filters(f -> f.stripPrefix(2).prefixPath("/httpbin")
											.localResponseCache(Duration.ofMillis(100), null))
									.uri(uri))
					.route("min_sized_prefix_local_response_cache_java_test",
							r -> r.path("/{namespace}/one-byte-cache/**").and().host("{sub}.localresponsecache.org")
									.filters(f -> f.stripPrefix(2).prefixPath("/httpbin").localResponseCache(null,
											DataSize.ofBytes(1L)))
									.uri(uri))
					.build();
		}

	}

}
