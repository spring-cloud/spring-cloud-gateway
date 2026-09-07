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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Weigher;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jspecify.annotations.Nullable;

import org.springframework.cloud.gateway.server.mvc.common.MvcUtils;
import org.springframework.cloud.gateway.server.mvc.common.Shortcut;
import org.springframework.cloud.gateway.server.mvc.config.GatewayMvcProperties;
import org.springframework.cloud.gateway.server.mvc.handler.GatewayServerResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.util.CollectionUtils;
import org.springframework.util.MultiValueMap;
import org.springframework.util.unit.DataSize;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.function.AsyncServerResponse;
import org.springframework.web.servlet.function.HandlerFilterFunction;
import org.springframework.web.servlet.function.HandlerFunction;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;

/**
 * {@link HandlerFilterFunction HandlerFilterFunctions} that cache HTTP responses, so
 * latency and upstream overhead are reduced. Mirrors the semantics of the WebFlux
 * {@literal LocalResponseCache} filter.
 *
 * @author Ingo Griebsch
 * @author Nikita Kibitkin
 */
public abstract class ResponseCacheFilterFunctions {

	private static final Duration DEFAULT_TIME_TO_LIVE = Duration.ofMinutes(5);

	private ResponseCacheFilterFunctions() {
	}

	@Shortcut
	public static HandlerFilterFunction<ServerResponse, ServerResponse> localResponseCache() {
		return localResponseCache(null, null);
	}

	@Shortcut({ "timeToLive" })
	public static HandlerFilterFunction<ServerResponse, ServerResponse> localResponseCache(
			@Nullable Duration timeToLive) {
		return localResponseCache(timeToLive, null);
	}

	@Shortcut({ "timeToLive", "size" })
	public static HandlerFilterFunction<ServerResponse, ServerResponse> localResponseCache(
			@Nullable Duration timeToLive, @Nullable DataSize size) {
		Duration ttl = timeToLive != null ? timeToLive : DEFAULT_TIME_TO_LIVE;
		return new ResponseCacheFilter(
				new ResponseCacheManager(new CacheKeyGenerator(), createCache(ttl, size), ttl, Clock.systemUTC()));
	}

	private static Cache<String, Object> createCache(Duration timeToLive, @Nullable DataSize size) {
		Caffeine<Object, Object> caffeine = Caffeine.newBuilder().expireAfterWrite(timeToLive);
		if (size != null) {
			return caffeine.maximumWeight(size.toBytes()).weigher(new CachedResponseWeigher()).build();
		}
		return caffeine.build();
	}

	/**
	 * A {@link HandlerFilterFunction} that serves responses from the local cache when
	 * possible and populates the cache from the upstream response otherwise.
	 */
	static class ResponseCacheFilter implements HandlerFilterFunction<ServerResponse, ServerResponse> {

		private final ResponseCacheManager responseCacheManager;

		private volatile @Nullable List<MediaType> streamingMediaTypes;

		ResponseCacheFilter(ResponseCacheManager responseCacheManager) {
			this.responseCacheManager = responseCacheManager;
		}

		@Override
		public ServerResponse filter(ServerRequest request, HandlerFunction<ServerResponse> next) throws Exception {
			if (!responseCacheManager.isRequestCacheable(request)) {
				return next.handle(request);
			}
			if (responseCacheManager.isNoCacheRequest(request)) {
				// no-cache: revalidate against the upstream, skip the cache entry update
				return next.handle(request);
			}
			String metadataKey = responseCacheManager.resolveMetadataKey(request);
			Optional<CachedResponse> cachedResponse = responseCacheManager.getFromCache(request, metadataKey);
			if (cachedResponse.isPresent()) {
				return responseCacheManager.processFromCache(metadataKey, cachedResponse.get());
			}
			ServerResponse response = next.handle(request);
			if (response instanceof AsyncServerResponse) {
				// an async response completes later and cannot be inspected or
				// captured here
				return response;
			}
			if (isStreamingResponse(request, response)) {
				// a streaming body must not be buffered into the cache
				return response;
			}
			return responseCacheManager.processFromUpstream(request, metadataKey, response);
		}

		private boolean isStreamingResponse(ServerRequest request, ServerResponse response) {
			MediaType contentType = response.headers().getContentType();
			if (contentType == null) {
				return false;
			}
			return streamingMediaTypes(request).stream().anyMatch(contentType::isCompatibleWith);
		}

		private List<MediaType> streamingMediaTypes(ServerRequest request) {
			List<MediaType> mediaTypes = this.streamingMediaTypes;
			if (mediaTypes == null) {
				mediaTypes = MvcUtils.getApplicationContext(request)
					.getBeanProvider(GatewayMvcProperties.class)
					.getIfAvailable(GatewayMvcProperties::new)
					.getStreamingMediaTypes();
				this.streamingMediaTypes = mediaTypes;
			}
			return mediaTypes;
		}

	}

	/**
	 * Caches responses and their metadata and applies the cache related response header
	 * mutations on both the cached and the upstream exchange path.
	 */
	static class ResponseCacheManager {

		private static final Log LOGGER = LogFactory.getLog(ResponseCacheManager.class);

		private static final List<String> FORBIDDEN_CACHE_CONTROL_VALUES = List.of("private", "no-store");

		private static final List<HttpStatusCode> STATUSES_TO_CACHE = List.of(HttpStatus.OK, HttpStatus.PARTIAL_CONTENT,
				HttpStatus.MOVED_PERMANENTLY);

		private static final String VARY_WILDCARD = "*";

		private static final Pattern NO_CACHE_PATTERN = Pattern.compile(".*(\\s|,|^)no-cache(\\s|,|$).*");

		private static final String MAX_AGE_PREFIX = "max-age=";

		private final CacheKeyGenerator cacheKeyGenerator;

		private final Cache<String, Object> cache;

		private final Duration timeToLive;

		private final Clock clock;

		ResponseCacheManager(CacheKeyGenerator cacheKeyGenerator, Cache<String, Object> cache, Duration timeToLive,
				Clock clock) {
			this.cacheKeyGenerator = cacheKeyGenerator;
			this.cache = cache;
			this.timeToLive = timeToLive;
			this.clock = clock;
		}

		boolean isRequestCacheable(ServerRequest request) {
			return HttpMethod.GET.equals(request.method()) && !hasRequestBody(request)
					&& isCacheControlAllowed(request.headers().asHttpHeaders());
		}

		boolean isNoCacheRequest(ServerRequest request) {
			String cacheControl = request.headers().asHttpHeaders().getCacheControl();
			return cacheControl != null && NO_CACHE_PATTERN.matcher(cacheControl).matches();
		}

		String resolveMetadataKey(ServerRequest request) {
			return cacheKeyGenerator.generateMetadataKey(request);
		}

		Optional<CachedResponse> getFromCache(ServerRequest request, String metadataKey) {
			List<String> varyOnHeaders = getIfPresent(metadataKey) instanceof CachedResponseMetadata metadata
					? metadata.varyOnHeaders() : Collections.emptyList();
			String key = cacheKeyGenerator.generateKey(request, varyOnHeaders);
			return getIfPresent(key) instanceof CachedResponse cachedResponse ? Optional.of(cachedResponse)
					: Optional.empty();
		}

		ServerResponse processFromUpstream(ServerRequest request, String metadataKey, ServerResponse response) {
			if (!isResponseCacheable(response)) {
				return response;
			}
			CachedResponseMetadata metadata = new CachedResponseMetadata(response.headers().getVary());
			String key = cacheKeyGenerator.generateKey(request, metadata.varyOnHeaders());
			try {
				applyAfterCacheMutations(response.headers(), clock.instant());
			}
			catch (UnsupportedOperationException ex) {
				// the response implementation exposes read-only headers (e.g. a
				// replacement built with ServerResponse.ok()); the entry is still
				// cached and cache hits receive the mutations
			}
			// the body cannot be read here without breaking the deferred proxy write,
			// so capture the bytes while they are written and fill the cache afterwards
			return new CachingServerResponse(response, this, metadataKey, metadata, key);
		}

		void cacheCapturedResponse(String metadataKey, CachedResponseMetadata metadata, String key,
				HttpStatusCode statusCode, HttpHeaders headers, byte[] body) {
			if (!STATUSES_TO_CACHE.contains(statusCode)) {
				// the status was mutated after the response was wrapped (e.g. by an
				// outer SetStatus filter)
				return;
			}
			if (headers.getContentLength() > -1 && headers.getContentLength() != body.length) {
				// the framing header does not match the written body, so something
				// outside the filter altered the exchange; do not cache the anomaly
				return;
			}
			putInCache(metadataKey, metadata);
			putInCache(key, new CachedResponse(statusCode, headers, body, clock.instant()));
		}

		ServerResponse processFromCache(String metadataKey, CachedResponse cachedResponse) {
			putInCache(metadataKey, new CachedResponseMetadata(cachedResponse.headers().getVary()));
			HttpHeaders responseHeaders = new HttpHeaders();
			responseHeaders.addAll(cachedResponse.headers());
			applyAfterCacheMutations(responseHeaders, cachedResponse.timestamp());
			return GatewayServerResponse.status(cachedResponse.statusCode())
				.headers(headers -> headers.addAll(responseHeaders))
				.build((servletRequest, servletResponse) -> {
					servletResponse.getOutputStream().write(cachedResponse.body());
					return null;
				});
		}

		private boolean isResponseCacheable(ServerResponse response) {
			return STATUSES_TO_CACHE.contains(response.statusCode()) && isCacheControlAllowed(response.headers())
					&& !isVaryWildcard(response.headers());
		}

		private void applyAfterCacheMutations(HttpHeaders headers, Instant cachedAt) {
			headers.remove(HttpHeaders.PRAGMA);
			headers.remove(HttpHeaders.EXPIRES);
			long maxAgeInSeconds = calculateMaxAgeInSeconds(cachedAt);
			rewriteCacheControlMaxAge(headers, maxAgeInSeconds);
			reconcileCacheControlDirectives(headers, maxAgeInSeconds);
		}

		private long calculateMaxAgeInSeconds(Instant cachedAt) {
			if (timeToLive.getSeconds() < 0) {
				return 0;
			}
			Duration elapsed = Duration.between(cachedAt, clock.instant());
			return Math.max(0, timeToLive.minus(elapsed).getSeconds());
		}

		private static void rewriteCacheControlMaxAge(HttpHeaders headers, long maxAgeInSeconds) {
			List<String> cacheControlValues = headers.getOrEmpty(HttpHeaders.CACHE_CONTROL);
			List<String> newCacheControlValues = new ArrayList<>();
			boolean maxAgePresent = cacheControlValues.stream().anyMatch(value -> value.contains(MAX_AGE_PREFIX));
			if (maxAgePresent) {
				for (String value : cacheControlValues) {
					if (value.contains(MAX_AGE_PREFIX)) {
						value = value.replaceFirst("\\bmax-age=\\d+\\b", MAX_AGE_PREFIX + maxAgeInSeconds);
					}
					newCacheControlValues.add(value);
				}
			}
			else {
				newCacheControlValues.addAll(cacheControlValues);
				newCacheControlValues.add(MAX_AGE_PREFIX + maxAgeInSeconds);
			}
			headers.remove(HttpHeaders.CACHE_CONTROL);
			headers.addAll(HttpHeaders.CACHE_CONTROL, newCacheControlValues);
		}

		private static void reconcileCacheControlDirectives(HttpHeaders headers, long maxAgeInSeconds) {
			String cacheControl = headers.getCacheControl();
			if (cacheControl == null) {
				return;
			}
			if (maxAgeInSeconds > 0) {
				headers.setCacheControl(Arrays.stream(cacheControl.split("\\s*,\\s*"))
					.filter(directive -> !directive.matches("must-revalidate|no-cache|no-store"))
					.collect(Collectors.joining(",")));
			}
			else {
				// 'max-age' is present, so appending directives with commas is safe
				StringBuilder newCacheControl = new StringBuilder(cacheControl);
				if (!cacheControl.contains("no-cache")) {
					newCacheControl.append(",no-cache");
				}
				if (!cacheControl.contains("must-revalidate")) {
					newCacheControl.append(",must-revalidate");
				}
				headers.setCacheControl(newCacheControl.toString());
			}
		}

		private static boolean isCacheControlAllowed(HttpHeaders headers) {
			return headers.getOrEmpty(HttpHeaders.CACHE_CONTROL)
				.stream()
				.noneMatch(FORBIDDEN_CACHE_CONTROL_VALUES::contains);
		}

		private static boolean isVaryWildcard(HttpHeaders headers) {
			return headers.getOrEmpty(HttpHeaders.VARY).stream().anyMatch(VARY_WILDCARD::equals);
		}

		private static boolean hasRequestBody(ServerRequest request) {
			return request.headers().asHttpHeaders().getContentLength() > 0;
		}

		private @Nullable Object getIfPresent(String key) {
			try {
				return cache.getIfPresent(key);
			}
			catch (RuntimeException e) {
				LOGGER.error("Error reading from cache. Data will not come from cache.", e);
				return null;
			}
		}

		private void putInCache(String key, Object value) {
			try {
				cache.put(key, value);
			}
			catch (RuntimeException e) {
				LOGGER.error("Error writing into cache. Data will not be cached.", e);
			}
		}

	}

	/**
	 * Creates cache keys based on a {@link ServerRequest} and the headers the cached
	 * response varies on.
	 */
	static class CacheKeyGenerator {

		private static final String KEY_SEPARATOR = ";";

		private static final String METADATA_KEY_PREFIX = "META_";

		private final ThreadLocal<MessageDigest> messageDigest = ThreadLocal.withInitial(() -> {
			try {
				return MessageDigest.getInstance("MD5");
			}
			catch (NoSuchAlgorithmException e) {
				throw new IllegalStateException("Error creating CacheKeyGenerator", e);
			}
		});

		String generateMetadataKey(ServerRequest request) {
			return METADATA_KEY_PREFIX + generateKey(request, Collections.emptyList());
		}

		String generateKey(ServerRequest request, List<String> varyOnHeaders) {
			byte[] digest = messageDigest.get().digest(generateRawKey(request, varyOnHeaders));
			return Base64.getEncoder().encodeToString(digest);
		}

		private byte[] generateRawKey(ServerRequest request, List<String> varyOnHeaders) {
			StringBuilder rawKey = new StringBuilder();
			rawKey.append(request.uri()).append(KEY_SEPARATOR);
			rawKey.append(headerKeyValue(request, HttpHeaders.AUTHORIZATION, KEY_SEPARATOR)).append(KEY_SEPARATOR);
			rawKey.append(cookiesKeyValue(request)).append(KEY_SEPARATOR);
			varyOnHeaders.stream()
				.sorted()
				.forEach(header -> rawKey.append(headerKeyValue(request, header, ",")).append(KEY_SEPARATOR));
			return rawKey.toString().getBytes(StandardCharsets.UTF_8);
		}

		private static String headerKeyValue(ServerRequest request, String header, String valueSeparator) {
			List<String> values = request.headers().asHttpHeaders().get(header);
			if (values == null) {
				return "";
			}
			return header + "=" + values.stream().sorted().collect(Collectors.joining(valueSeparator));
		}

		private static String cookiesKeyValue(ServerRequest request) {
			MultiValueMap<String, Cookie> cookies = request.cookies();
			if (CollectionUtils.isEmpty(cookies)) {
				return "";
			}
			return cookies.values()
				.stream()
				.flatMap(Collection::stream)
				.map(cookie -> cookie.getName() + "=" + cookie.getValue())
				.sorted()
				.collect(Collectors.joining(KEY_SEPARATOR));
		}

	}

	/**
	 * A cached HTTP response.
	 *
	 * @param statusCode the status code of the cached response
	 * @param headers the headers of the cached response, as received from the upstream
	 * @param body the body of the cached response
	 * @param timestamp the moment the response was cached
	 */
	record CachedResponse(HttpStatusCode statusCode, HttpHeaders headers, byte[] body, Instant timestamp) {

	}

	/**
	 * The metadata of a cached HTTP response.
	 *
	 * @param varyOnHeaders the request headers the cached response varies on
	 */
	record CachedResponseMetadata(List<String> varyOnHeaders) {

	}

	/**
	 * A {@link ServerResponse} decorator that captures the bytes the delegate writes and
	 * fills the cache once the delegate has been written successfully. Capturing at write
	 * time keeps the regular (deferred) proxy write path intact and guarantees that the
	 * cached body always belongs to the response that produced it.
	 */
	private static final class CachingServerResponse implements GatewayServerResponse {

		private final ServerResponse delegate;

		private final ResponseCacheManager responseCacheManager;

		private final String metadataKey;

		private final CachedResponseMetadata metadata;

		private final String key;

		CachingServerResponse(ServerResponse delegate, ResponseCacheManager responseCacheManager, String metadataKey,
				CachedResponseMetadata metadata, String key) {
			this.delegate = delegate;
			this.responseCacheManager = responseCacheManager;
			this.metadataKey = metadataKey;
			this.metadata = metadata;
			this.key = key;
		}

		@Override
		public HttpStatusCode statusCode() {
			return delegate.statusCode();
		}

		@Override
		public void setStatusCode(HttpStatusCode statusCode) {
			// matches the SetStatus filter semantics: status mutation is only
			// supported for gateway-produced responses
			if (delegate instanceof GatewayServerResponse gatewayServerResponse) {
				gatewayServerResponse.setStatusCode(statusCode);
			}
		}

		@Override
		public HttpHeaders headers() {
			return delegate.headers();
		}

		@Override
		public MultiValueMap<String, Cookie> cookies() {
			return delegate.cookies();
		}

		@Override
		public @Nullable ModelAndView writeTo(HttpServletRequest request, HttpServletResponse response, Context context)
				throws ServletException, IOException {
			BodyCapturingResponseWrapper capturingResponse = new BodyCapturingResponseWrapper(response);
			ModelAndView modelAndView;
			try {
				modelAndView = delegate.writeTo(request, capturingResponse, context);
			}
			finally {
				// the capturing writer buffers; flush so the client receives the tail
				// even when the write turns out not to be cacheable
				capturingResponse.flushWriter();
			}
			if (isCompletedCacheableWrite(request, capturingResponse, modelAndView)) {
				responseCacheManager.cacheCapturedResponse(metadataKey, metadata, key, delegate.statusCode(),
						headersToCache(capturingResponse), capturingResponse.getCapturedBody());
			}
			else {
				capturingResponse.stopCapturing();
			}
			return modelAndView;
		}

		private boolean isCompletedCacheableWrite(HttpServletRequest request, BodyCapturingResponseWrapper response,
				@Nullable ModelAndView modelAndView) {
			if (modelAndView != null || request.isAsyncStarted()) {
				// the body is produced outside this call (view rendering or async
				// dispatch), so nothing was captured
				return false;
			}
			if (response.getStatus() != delegate.statusCode().value()) {
				// the delegate answered differently than advertised, e.g. 304 Not
				// Modified to a conditional request, so the captured body is not the
				// full response
				return false;
			}
			if (response.hasCaptureErrors()) {
				// a swallowed writer error means the captured body may be truncated
				return false;
			}
			return true;
		}

		private HttpHeaders headersToCache(BodyCapturingResponseWrapper response) {
			HttpHeaders headers = new HttpHeaders();
			headers.addAll(delegate.headers());
			// some headers only exist on the servlet response, e.g. the Content-Type a
			// message converter chose while writing a replacement response
			if (headers.getContentType() == null && response.getContentType() != null) {
				headers.set(HttpHeaders.CONTENT_TYPE, response.getContentType());
			}
			return headers;
		}

	}

	/**
	 * A {@link HttpServletResponseWrapper} that copies everything written to the response
	 * body into a buffer while passing it through to the underlying response.
	 */
	private static final class BodyCapturingResponseWrapper extends HttpServletResponseWrapper {

		private final ByteArrayOutputStream capturedBody = new ByteArrayOutputStream();

		private volatile boolean capturing = true;

		private @Nullable ServletOutputStream outputStream;

		private @Nullable PrintWriter writer;

		BodyCapturingResponseWrapper(HttpServletResponse response) {
			super(response);
		}

		byte[] getCapturedBody() {
			flushWriter();
			return capturedBody.toByteArray();
		}

		boolean hasCaptureErrors() {
			return writer != null && writer.checkError();
		}

		void flushWriter() {
			if (writer != null) {
				writer.flush();
			}
		}

		void stopCapturing() {
			// async writes (e.g. a locally produced SSE stream) may continue through
			// this wrapper long after the routing call returned; without this the
			// buffer would grow for the lifetime of the connection
			capturing = false;
			capturedBody.reset();
		}

		@Override
		public ServletOutputStream getOutputStream() throws IOException {
			if (outputStream == null) {
				outputStream = new BodyCapturingOutputStream(super.getOutputStream());
			}
			return outputStream;
		}

		@Override
		public PrintWriter getWriter() throws IOException {
			if (writer == null) {
				String characterEncoding = getCharacterEncoding();
				Charset charset = characterEncoding != null ? Charset.forName(characterEncoding)
						: StandardCharsets.ISO_8859_1;
				writer = new PrintWriter(new OutputStreamWriter(getOutputStream(), charset));
			}
			return writer;
		}

		/**
		 * A {@link ServletOutputStream} that tees everything written to it into the
		 * capture buffer while capturing is active.
		 */
		private final class BodyCapturingOutputStream extends ServletOutputStream {

			private final ServletOutputStream delegate;

			BodyCapturingOutputStream(ServletOutputStream delegate) {
				this.delegate = delegate;
			}

			@Override
			public void write(int b) throws IOException {
				delegate.write(b);
				if (capturing) {
					capturedBody.write(b);
				}
			}

			@Override
			public void write(byte[] b, int off, int len) throws IOException {
				delegate.write(b, off, len);
				if (capturing) {
					capturedBody.write(b, off, len);
				}
			}

			@Override
			public void flush() throws IOException {
				delegate.flush();
			}

			@Override
			public void close() throws IOException {
				delegate.close();
			}

			@Override
			public boolean isReady() {
				return delegate.isReady();
			}

			@Override
			public void setWriteListener(WriteListener writeListener) {
				delegate.setWriteListener(writeListener);
			}

		}

	}

	/**
	 * Weighs cache entries by the size of the cached response body.
	 */
	private static final class CachedResponseWeigher implements Weigher<String, Object> {

		@Override
		public int weigh(String key, Object value) {
			if (value instanceof CachedResponse cachedResponse) {
				long contentLength = cachedResponse.headers().getContentLength();
				return (int) Math.min(Integer.MAX_VALUE,
						contentLength > -1 ? contentLength : cachedResponse.body().length);
			}
			return 0;
		}

	}

	public static class FilterSupplier extends SimpleFilterSupplier {

		public FilterSupplier() {
			super(ResponseCacheFilterFunctions.class);
		}

	}

}
