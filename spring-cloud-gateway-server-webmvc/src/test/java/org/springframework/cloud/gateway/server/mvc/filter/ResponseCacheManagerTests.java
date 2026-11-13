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

package org.springframework.cloud.gateway.server.mvc.filter;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import com.github.benmanes.caffeine.cache.Caffeine;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import org.springframework.cloud.gateway.server.mvc.common.MvcUtils;
import org.springframework.cloud.gateway.server.mvc.filter.ResponseCacheFilterFunctions.CacheKeyGenerator;
import org.springframework.cloud.gateway.server.mvc.filter.ResponseCacheFilterFunctions.CachedResponse;
import org.springframework.cloud.gateway.server.mvc.filter.ResponseCacheFilterFunctions.CachedResponseMetadata;
import org.springframework.cloud.gateway.server.mvc.filter.ResponseCacheFilterFunctions.ResponseCacheManager;
import org.springframework.cloud.gateway.server.mvc.handler.GatewayServerResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Ingo Griebsch
 * @author Nikita Kibitkin
 */
public class ResponseCacheManagerTests {

	private static final Duration TIME_TO_LIVE = Duration.ofMinutes(5);

	private final CacheKeyGenerator cacheKeyGenerator = new CacheKeyGenerator();

	@Test
	public void requestIsCacheableWhenBodilessGet() {
		ResponseCacheManager manager = manager(Clock.systemUTC());
		assertThat(manager.isRequestCacheable(request("GET", "/resource").build())).isTrue();
	}

	@ParameterizedTest
	@ValueSource(strings = { "POST", "PUT", "DELETE", "HEAD" })
	public void requestIsNotCacheableWhenMethodIsNotGet(String method) {
		ResponseCacheManager manager = manager(Clock.systemUTC());
		assertThat(manager.isRequestCacheable(request(method, "/resource").build())).isFalse();
	}

	@Test
	public void requestIsNotCacheableWhenBodyIsPresent() {
		MockHttpServletRequest servletRequest = new MockHttpServletRequest("GET", "/resource");
		servletRequest.setContent("body".getBytes(StandardCharsets.UTF_8));
		servletRequest.addHeader(HttpHeaders.CONTENT_LENGTH, 4);
		ResponseCacheManager manager = manager(Clock.systemUTC());
		assertThat(manager.isRequestCacheable(ServerRequest.create(servletRequest, Collections.emptyList()))).isFalse();
	}

	@ParameterizedTest
	@ValueSource(strings = { "private", "no-store" })
	public void requestIsNotCacheableWhenCacheControlForbidsIt(String cacheControl) {
		ResponseCacheManager manager = manager(Clock.systemUTC());
		assertThat(manager
			.isRequestCacheable(request("GET", "/resource").header(HttpHeaders.CACHE_CONTROL, cacheControl).build()))
			.isFalse();
	}

	@ParameterizedTest
	@ValueSource(strings = { "no-cache, no-store", "max-age=0, no-store", "NO-STORE", "no-transform ,no-store",
			"private, no-store", "max-age=60, private", "No-Store", "private=\"Set-Cookie\"" })
	public void requestIsNotCacheableWhenForbiddenDirectiveIsAmongOthers(String cacheControl) {
		ResponseCacheManager manager = manager(Clock.systemUTC());
		assertThat(manager
			.isRequestCacheable(request("GET", "/resource").header(HttpHeaders.CACHE_CONTROL, cacheControl).build()))
			.isFalse();
	}

	@Test
	public void requestIsNotCacheableWhenForbiddenDirectiveIsInSeparateHeaderLine() {
		ResponseCacheManager manager = manager(Clock.systemUTC());
		assertThat(manager.isRequestCacheable(request("GET", "/resource").header(HttpHeaders.CACHE_CONTROL, "max-age=0")
			.header(HttpHeaders.CACHE_CONTROL, "no-store")
			.build())).isFalse();
	}

	@ParameterizedTest
	@ValueSource(strings = { "max-age=0, no-cache", "max-age=0, example=\"a,no-store,b\"", "example=\"private\"",
			"example=no-store-extension", "no-cache", "s-maxage=60" })
	public void requestIsCacheableWhenForbiddenWordIsNotADirective(String cacheControl) {
		ResponseCacheManager manager = manager(Clock.systemUTC());
		assertThat(manager
			.isRequestCacheable(request("GET", "/resource").header(HttpHeaders.CACHE_CONTROL, cacheControl).build()))
			.isTrue();
	}

	@ParameterizedTest
	@ValueSource(strings = { "private", "no-store", "private, no-store",
			"no-cache, no-store, max-age=0, must-revalidate", "max-age=60, private", "public,private", "PRIVATE",
			"No-Store", "  max-age=60 ,  no-store  ", "private=\"Set-Cookie\"" })
	public void responseIsNotCachedWhenForbiddenDirectiveIsPresent(String cacheControl) {
		assertThat(isResponseCached(cacheControl)).isFalse();
	}

	@Test
	public void responseIsNotCachedWhenForbiddenDirectiveIsInSeparateHeaderLine() {
		HttpHeaders headers = new HttpHeaders();
		headers.add(HttpHeaders.CACHE_CONTROL, "max-age=60");
		headers.add(HttpHeaders.CACHE_CONTROL, "private");
		assertThat(isResponseCached(headers)).isFalse();
	}

	@ParameterizedTest
	@ValueSource(strings = { "example=\"a,b\", private", "example=\"a,b\", no-store, max-age=60",
			"example=\"a\\\",b\", no-store", "private, example=\"a,b\"" })
	public void responseIsNotCachedWhenForbiddenDirectiveIsNextToQuotedArgument(String cacheControl) {
		assertThat(isResponseCached(cacheControl)).isFalse();
	}

	@Test
	public void responseIsNotCachedWhenQuotedArgumentIsUnterminated() {
		assertThat(isResponseCached("max-age=60, example=\"a, public")).isFalse();
	}

	@ParameterizedTest
	@ValueSource(strings = { "public, max-age=60, example=\"a,no-store,b\"", "example=\"private\"",
			"example=\"a, private\", max-age=60", "example=\"a\\\",no-store\"", "example=\"a\\\\\", max-age=60",
			"example=no-store-extension", "public, max-age=60", "no-cache", "max-age=60, no-transform", "s-maxage=60",
			"private-extension" })
	public void responseIsCachedWhenForbiddenWordIsNotADirective(String cacheControl) {
		assertThat(isResponseCached(cacheControl)).isTrue();
	}

	@ParameterizedTest
	@ValueSource(strings = { "no-cache", "private,no-cache", " no-cache", "no-cache ", "s-no-cache, no-cache" })
	public void noCacheRequestIsDetected(String cacheControl) {
		ResponseCacheManager manager = manager(Clock.systemUTC());
		assertThat(manager
			.isNoCacheRequest(request("GET", "/resource").header(HttpHeaders.CACHE_CONTROL, cacheControl).build()))
			.isTrue();
	}

	@ParameterizedTest
	@ValueSource(strings = { "no-store", "no-store, wrong-no-cache", "s-no-cache" })
	public void noCacheRequestIsNotDetected(String cacheControl) {
		ResponseCacheManager manager = manager(Clock.systemUTC());
		assertThat(manager
			.isNoCacheRequest(request("GET", "/resource").header(HttpHeaders.CACHE_CONTROL, cacheControl).build()))
			.isFalse();
	}

	@Test
	public void responseIsCachedAndServedFromCache() throws Exception {
		ResponseCacheManager manager = manager(Clock.systemUTC());
		ServerRequest request = request("GET", "/resource").build();
		String metadataKey = manager.resolveMetadataKey(request);

		assertThat(manager.getFromCache(request, metadataKey)).isEmpty();

		ServerResponse response = manager.processFromUpstream(request, metadataKey, upstreamResponse("the body"));
		// the cache is filled once the response body has been written
		assertThat(manager.getFromCache(request, metadataKey)).isEmpty();
		MockHttpServletResponse servletResponse = write(response);

		Optional<CachedResponse> cachedResponse = manager.getFromCache(request, metadataKey);
		assertThat(cachedResponse).isPresent();
		assertThat(cachedResponse.get().body()).isEqualTo("the body".getBytes(StandardCharsets.UTF_8));
		// the body is written through to the client while being captured
		assertThat(servletResponse.getContentAsByteArray()).isEqualTo("the body".getBytes(StandardCharsets.UTF_8));
	}

	@Test
	public void bodyWrittenThroughWriterIsCachedAndPassedThrough() throws Exception {
		ResponseCacheManager manager = manager(Clock.systemUTC());
		ServerRequest request = request("GET", "/resource").build();
		String metadataKey = manager.resolveMetadataKey(request);

		ServerResponse response = manager.processFromUpstream(request, metadataKey,
				GatewayServerResponse.status(HttpStatus.OK).build((servletRequest, servletResponse) -> {
					servletResponse.getWriter().write("the body");
					return null;
				}));
		MockHttpServletResponse servletResponse = write(response);

		Optional<CachedResponse> cachedResponse = manager.getFromCache(request, metadataKey);
		assertThat(cachedResponse).isPresent();
		assertThat(cachedResponse.get().body()).isEqualTo("the body".getBytes(StandardCharsets.UTF_8));
		// the writer buffers, so the write is only complete once the filter flushed it
		assertThat(servletResponse.getContentAsByteArray()).isEqualTo("the body".getBytes(StandardCharsets.UTF_8));
	}

	@Test
	public void upstreamResponseHeadersReceiveCacheControlMutations() {
		Instant now = Instant.parse("2025-01-01T10:00:00Z");
		ResponseCacheManager manager = manager(Clock.fixed(now, ZoneOffset.UTC));
		ServerRequest request = request("GET", "/resource").build();
		String metadataKey = manager.resolveMetadataKey(request);

		ServerResponse result = manager.processFromUpstream(request, metadataKey, upstreamResponse("the body"));

		assertThat(result.headers().getCacheControl()).isEqualTo("max-age=" + TIME_TO_LIVE.getSeconds());
	}

	@Test
	public void notModifiedWriteIsNotCached() throws Exception {
		ResponseCacheManager manager = manager(Clock.systemUTC());
		ServerRequest request = request("GET", "/resource").build();
		String metadataKey = manager.resolveMetadataKey(request);

		HttpHeaders responseHeaders = new HttpHeaders();
		responseHeaders.setETag("\"v1\"");
		ServerResponse result = manager.processFromUpstream(request, metadataKey,
				upstreamResponse("the body", HttpStatus.OK, responseHeaders));
		MockHttpServletRequest conditionalRequest = new MockHttpServletRequest("GET", "/resource");
		conditionalRequest.addHeader(HttpHeaders.IF_NONE_MATCH, "\"v1\"");
		MockHttpServletResponse servletResponse = write(result, conditionalRequest);

		assertThat(servletResponse.getStatus()).isEqualTo(HttpStatus.NOT_MODIFIED.value());
		// the delegate answered 304 without a body, so nothing must be cached
		assertThat(manager.getFromCache(request, metadataKey)).isEmpty();
	}

	@Test
	public void mismatchedContentLengthIsNotCached() {
		ResponseCacheManager manager = manager(Clock.systemUTC());
		ServerRequest request = request("GET", "/resource").build();
		String metadataKey = manager.resolveMetadataKey(request);
		String key = cacheKeyGenerator.generateKey(request, Collections.emptyList());

		HttpHeaders headers = new HttpHeaders();
		headers.setContentLength(1);
		manager.cacheCapturedResponse(metadataKey, new CachedResponseMetadata(Collections.emptyList()), key,
				HttpStatus.OK, headers, "the body".getBytes(StandardCharsets.UTF_8));

		// the framing header does not match the written body, so the anomaly must
		// not be cached
		assertThat(manager.getFromCache(request, metadataKey)).isEmpty();
	}

	@Test
	public void veryLargeTimeToLiveDoesNotOverflowMaxAge() {
		Instant now = Instant.parse("2025-01-01T10:00:00Z");
		Duration timeToLive = Duration.ofDays(36500);
		ResponseCacheManager manager = new ResponseCacheManager(cacheKeyGenerator, Caffeine.newBuilder().build(),
				timeToLive, Clock.fixed(now, ZoneOffset.UTC));
		CachedResponse cachedResponse = new CachedResponse(HttpStatus.OK, new HttpHeaders(),
				"the body".getBytes(StandardCharsets.UTF_8), now);

		ServerResponse response = manager.processFromCache("META_key", cachedResponse);

		assertThat(response.headers().getCacheControl()).isEqualTo("max-age=" + timeToLive.getSeconds());
	}

	@Test
	public void responseIsNotCachedWhenStatusCodeIsNotCacheable() {
		ResponseCacheManager manager = manager(Clock.systemUTC());
		ServerRequest request = request("GET", "/resource").build();
		String metadataKey = manager.resolveMetadataKey(request);

		ServerResponse response = upstreamResponse("the body", HttpStatus.BAD_GATEWAY, new HttpHeaders());
		ServerResponse result = manager.processFromUpstream(request, metadataKey, response);

		assertThat(result).isSameAs(response);
		assertThat(manager.getFromCache(request, metadataKey)).isEmpty();
	}

	@ParameterizedTest
	@EnumSource(value = HttpStatus.class, names = { "OK", "PARTIAL_CONTENT", "MOVED_PERMANENTLY" })
	public void responseIsCachedWhenStatusCodeIsCacheable(HttpStatus status) throws Exception {
		ResponseCacheManager manager = manager(Clock.systemUTC());
		ServerRequest request = request("GET", "/resource").build();
		String metadataKey = manager.resolveMetadataKey(request);

		write(manager.processFromUpstream(request, metadataKey,
				upstreamResponse("the body", status, new HttpHeaders())));

		Optional<CachedResponse> cachedResponse = manager.getFromCache(request, metadataKey);
		assertThat(cachedResponse).isPresent();
		assertThat(cachedResponse.get().statusCode()).isEqualTo(status);
	}

	@Test
	public void cachedEntriesVaryOnHeadersFromResponseVary() throws Exception {
		ResponseCacheManager manager = manager(Clock.systemUTC());
		ServerRequest request = request("GET", "/resource").header("X-Custom", "one").build();
		String metadataKey = manager.resolveMetadataKey(request);

		HttpHeaders responseHeaders = new HttpHeaders();
		responseHeaders.add(HttpHeaders.VARY, "X-Custom");
		write(manager.processFromUpstream(request, metadataKey,
				upstreamResponse("one", HttpStatus.OK, responseHeaders)));

		ServerRequest sameVaryValue = request("GET", "/resource").header("X-Custom", "one").build();
		ServerRequest otherVaryValue = request("GET", "/resource").header("X-Custom", "two").build();
		assertThat(manager.getFromCache(sameVaryValue, manager.resolveMetadataKey(sameVaryValue))).isPresent();
		assertThat(manager.getFromCache(otherVaryValue, manager.resolveMetadataKey(otherVaryValue))).isEmpty();
	}

	@Test
	public void maxAgeIsRecalculatedFromEntryAge() {
		Instant now = Instant.parse("2025-01-01T10:00:00Z");
		ResponseCacheManager manager = manager(Clock.fixed(now, ZoneOffset.UTC));
		CachedResponse cachedResponse = new CachedResponse(HttpStatus.OK, new HttpHeaders(),
				"the body".getBytes(StandardCharsets.UTF_8), now.minusSeconds(120));

		ServerResponse response = manager.processFromCache("META_key", cachedResponse);

		assertThat(response.headers().getCacheControl()).isEqualTo("max-age=" + (TIME_TO_LIVE.getSeconds() - 120));
	}

	@Test
	public void existingMaxAgeIsRewrittenFromEntryAge() {
		Instant now = Instant.parse("2025-01-01T10:00:00Z");
		ResponseCacheManager manager = manager(Clock.fixed(now, ZoneOffset.UTC));
		HttpHeaders headers = new HttpHeaders();
		headers.setCacheControl("max-age=999");
		CachedResponse cachedResponse = new CachedResponse(HttpStatus.OK, headers,
				"the body".getBytes(StandardCharsets.UTF_8), now.minusSeconds(120));

		ServerResponse response = manager.processFromCache("META_key", cachedResponse);

		assertThat(response.headers().getCacheControl()).isEqualTo("max-age=" + (TIME_TO_LIVE.getSeconds() - 120));
	}

	@Test
	public void noCacheDirectivesAreStrippedWhenEntryIsFresh() {
		Instant now = Instant.parse("2025-01-01T10:00:00Z");
		ResponseCacheManager manager = manager(Clock.fixed(now, ZoneOffset.UTC));
		HttpHeaders headers = new HttpHeaders();
		headers.setCacheControl("no-cache, must-revalidate, max-age=999");
		CachedResponse cachedResponse = new CachedResponse(HttpStatus.OK, headers,
				"the body".getBytes(StandardCharsets.UTF_8), now.minusSeconds(120));

		ServerResponse response = manager.processFromCache("META_key", cachedResponse);

		assertThat(response.headers().getCacheControl()).isEqualTo("max-age=" + (TIME_TO_LIVE.getSeconds() - 120));
	}

	@Test
	public void expiredEntryYieldsMaxAgeZeroAndNoCacheDirectives() {
		Instant now = Instant.parse("2025-01-01T10:00:00Z");
		ResponseCacheManager manager = manager(Clock.fixed(now, ZoneOffset.UTC));
		CachedResponse cachedResponse = new CachedResponse(HttpStatus.OK, new HttpHeaders(),
				"the body".getBytes(StandardCharsets.UTF_8), now.minus(TIME_TO_LIVE).minusSeconds(1));

		ServerResponse response = manager.processFromCache("META_key", cachedResponse);

		assertThat(response.headers().getCacheControl()).contains("max-age=0")
			.contains("no-cache")
			.contains("must-revalidate");
	}

	@Test
	public void pragmaAndExpiresAreRemovedFromCachedResponse() {
		HttpHeaders headers = new HttpHeaders();
		headers.add(HttpHeaders.PRAGMA, "no-cache");
		headers.add(HttpHeaders.EXPIRES, "0");
		ResponseCacheManager manager = manager(Clock.systemUTC());
		CachedResponse cachedResponse = new CachedResponse(HttpStatus.OK, headers,
				"the body".getBytes(StandardCharsets.UTF_8), Instant.now());

		ServerResponse response = manager.processFromCache("META_key", cachedResponse);

		assertThat(response.headers().headerNames()).doesNotContain(HttpHeaders.PRAGMA, HttpHeaders.EXPIRES);
	}

	@Test
	public void keyIsStableForSameRequest() {
		ServerRequest request = request("GET", "/resource").build();
		ServerRequest sameRequest = request("GET", "/resource").build();
		assertThat(cacheKeyGenerator.generateKey(request, Collections.emptyList()))
			.isEqualTo(cacheKeyGenerator.generateKey(sameRequest, Collections.emptyList()));
	}

	@Test
	public void keyDiffersOnAuthorizationHeader() {
		ServerRequest request = request("GET", "/resource").header(HttpHeaders.AUTHORIZATION, "Bearer one").build();
		ServerRequest otherRequest = request("GET", "/resource").header(HttpHeaders.AUTHORIZATION, "Bearer two")
			.build();
		assertThat(cacheKeyGenerator.generateKey(request, Collections.emptyList()))
			.isNotEqualTo(cacheKeyGenerator.generateKey(otherRequest, Collections.emptyList()));
	}

	@Test
	public void keyDiffersOnCookies() {
		ServerRequest request = request("GET", "/resource").cookie("session", "one").build();
		ServerRequest otherRequest = request("GET", "/resource").cookie("session", "two").build();
		assertThat(cacheKeyGenerator.generateKey(request, Collections.emptyList()))
			.isNotEqualTo(cacheKeyGenerator.generateKey(otherRequest, Collections.emptyList()));
	}

	@Test
	public void keyDiffersOnVaryHeaderValues() {
		ServerRequest request = request("GET", "/resource").header("X-Custom", "one").build();
		ServerRequest otherRequest = request("GET", "/resource").header("X-Custom", "two").build();
		assertThat(cacheKeyGenerator.generateKey(request, List.of("X-Custom")))
			.isNotEqualTo(cacheKeyGenerator.generateKey(otherRequest, List.of("X-Custom")));
	}

	@Test
	public void keyIgnoresHeadersTheResponseDoesNotVaryOn() {
		ServerRequest request = request("GET", "/resource").header("X-Custom", "one").build();
		ServerRequest otherRequest = request("GET", "/resource").header("X-Custom", "two").build();
		assertThat(cacheKeyGenerator.generateKey(request, Collections.emptyList()))
			.isEqualTo(cacheKeyGenerator.generateKey(otherRequest, Collections.emptyList()));
	}

	@Test
	public void keyDiffersOnRouteId() {
		ServerRequest request = request("GET", "/resource").routeId("route-a").build();
		ServerRequest otherRequest = request("GET", "/resource").routeId("route-b").build();
		assertThat(cacheKeyGenerator.generateKey(request, Collections.emptyList()))
			.isNotEqualTo(cacheKeyGenerator.generateKey(otherRequest, Collections.emptyList()));
		assertThat(cacheKeyGenerator.generateMetadataKey(request))
			.isNotEqualTo(cacheKeyGenerator.generateMetadataKey(otherRequest));
	}

	@Test
	public void keyIsStableForSameRouteId() {
		ServerRequest request = request("GET", "/resource").routeId("route-a").build();
		ServerRequest sameRequest = request("GET", "/resource").routeId("route-a").build();
		assertThat(cacheKeyGenerator.generateKey(request, Collections.emptyList()))
			.isEqualTo(cacheKeyGenerator.generateKey(sameRequest, Collections.emptyList()));
	}

	@Test
	public void keyWithoutRouteIdDiffersFromKeyWithRouteId() {
		ServerRequest request = request("GET", "/resource").build();
		ServerRequest routedRequest = request("GET", "/resource").routeId("route-a").build();
		assertThat(cacheKeyGenerator.generateKey(request, Collections.emptyList())).isNotBlank()
			.isNotEqualTo(cacheKeyGenerator.generateKey(routedRequest, Collections.emptyList()));
	}

	@Test
	public void keyWithoutRouteIdHasNoRouteSegment() throws Exception {
		ServerRequest request = request("GET", "/resource").build();
		// uri;authorization;cookies; - the format used before keys were scoped to a route
		byte[] rawKey = (request.uri() + ";;;").getBytes(StandardCharsets.UTF_8);
		String expected = Base64.getEncoder().encodeToString(MessageDigest.getInstance("MD5").digest(rawKey));
		assertThat(cacheKeyGenerator.generateKey(request, Collections.emptyList())).isEqualTo(expected);
	}

	@Test
	public void entryCachedForOneRouteIsNotServedToAnotherRoute() throws Exception {
		ResponseCacheManager manager = manager(Clock.systemUTC());
		ServerRequest requestA = request("GET", "/resource").routeId("route-a").build();
		ServerRequest requestB = request("GET", "/resource").routeId("route-b").build();

		write(manager.processFromUpstream(requestA, manager.resolveMetadataKey(requestA), upstreamResponse("a")));

		assertThat(manager.getFromCache(requestA, manager.resolveMetadataKey(requestA))).isPresent();
		assertThat(manager.getFromCache(requestB, manager.resolveMetadataKey(requestB))).isEmpty();
	}

	private ResponseCacheManager manager(Clock clock) {
		return new ResponseCacheManager(cacheKeyGenerator, Caffeine.newBuilder().build(), TIME_TO_LIVE, clock);
	}

	private boolean isResponseCached(String cacheControl) {
		HttpHeaders headers = new HttpHeaders();
		headers.add(HttpHeaders.CACHE_CONTROL, cacheControl);
		return isResponseCached(headers);
	}

	private boolean isResponseCached(HttpHeaders headers) {
		ResponseCacheManager manager = manager(Clock.systemUTC());
		ServerRequest request = request("GET", "/resource").build();
		ServerResponse response = upstreamResponse("the body", HttpStatus.OK, headers);
		// a response that is not cacheable is returned as is
		return manager.processFromUpstream(request, manager.resolveMetadataKey(request), response) != response;
	}

	private static RequestBuilder request(String method, String path) {
		return new RequestBuilder(method, path);
	}

	private static ServerResponse upstreamResponse(String body) {
		return upstreamResponse(body, HttpStatus.OK, new HttpHeaders());
	}

	private static ServerResponse upstreamResponse(String body, HttpStatus status, HttpHeaders headers) {
		return GatewayServerResponse.status(status)
			.headers(httpHeaders -> httpHeaders.addAll(headers))
			.build((servletRequest, servletResponse) -> {
				servletResponse.getOutputStream().write(body.getBytes(StandardCharsets.UTF_8));
				return null;
			});
	}

	private static MockHttpServletResponse write(ServerResponse response) throws Exception {
		return write(response, new MockHttpServletRequest());
	}

	private static MockHttpServletResponse write(ServerResponse response, MockHttpServletRequest servletRequest)
			throws Exception {
		MockHttpServletResponse servletResponse = new MockHttpServletResponse();
		response.writeTo(servletRequest, servletResponse, new ServerResponse.Context() {
			@Override
			public List<HttpMessageConverter<?>> messageConverters() {
				return Collections.emptyList();
			}
		});
		return servletResponse;
	}

	private static final class RequestBuilder {

		private final MockHttpServletRequest servletRequest;

		private RequestBuilder(String method, String path) {
			this.servletRequest = new MockHttpServletRequest(method, path);
		}

		private RequestBuilder header(String name, String value) {
			servletRequest.addHeader(name, value);
			return this;
		}

		private RequestBuilder routeId(String routeId) {
			servletRequest.setAttribute(MvcUtils.GATEWAY_ROUTE_ID_ATTR, routeId);
			return this;
		}

		private RequestBuilder cookie(String name, String value) {
			servletRequest.setCookies(new Cookie(name, value));
			return this;
		}

		private ServerRequest build() {
			return ServerRequest.create(servletRequest, Collections.emptyList());
		}

	}

}
