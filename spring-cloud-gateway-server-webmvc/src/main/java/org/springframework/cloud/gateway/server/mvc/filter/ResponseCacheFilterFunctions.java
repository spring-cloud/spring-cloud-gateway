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

import static org.springframework.http.HttpHeaders.CACHE_CONTROL;
import static org.springframework.http.HttpHeaders.VARY;
import static org.springframework.http.HttpStatus.MOVED_PERMANENTLY;
import static org.springframework.http.HttpStatus.OK;
import static org.springframework.http.HttpStatus.PARTIAL_CONTENT;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.jspecify.annotations.Nullable;
import org.springframework.cache.Cache;
import org.springframework.cloud.gateway.server.mvc.filter.ResponseCacheFilterFunctions.CacheKeyFactory.CacheKey;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.util.StringUtils;
import org.springframework.util.unit.DataSize;
import org.springframework.web.servlet.function.HandlerFilterFunction;
import org.springframework.web.servlet.function.HandlerFunction;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;

/**
 * The central interface that enables the caching of certain HTTP responses, thereby
 * reducing latency and overhead for the upstream server.
 * <p>
 * I'm currently keeping all feature-specific code in this class. Whether and how the code
 * should be moved to separate classes and packages can be decided later.
 *
 * @author Ingo Griebsch
 */
public abstract class ResponseCacheFilterFunctions {

	private ResponseCacheFilterFunctions() {
	}

	public static HandlerFilterFunction<ServerResponse, ServerResponse> responseCache(Duration timeToLive,
			DataSize cacheSize) {
		return new ResponseCacheFilter();
	}

	/**
	 * A {@link HandlerFilterFunction} implementation that allows to cache certain HTTP
	 * responses.
	 *
	 * @author Ingo Griebsch
	 */
	static class ResponseCacheFilter implements HandlerFilterFunction<ServerResponse, ServerResponse> {

		@Override
		public ServerResponse filter(ServerRequest request, HandlerFunction<ServerResponse> next) throws Exception {
			// FIXME implement me...

			// If the request is not cacheable, simple continue with the next filter.
			if (!ServletUtils.isCacheable(request)) {
				return next.handle(request);
			}

			// FIXME Should we remember that this filter is now applied?

			// If the request should be revalidated continue with the request an put the
			// response in the cache.
			if (ServletUtils.shouldRevalidate(request)) {
				return cacheResponse(next.handle(request));
			}

			// Check if a response for this request is already cached.
			Optional<CachedResponse> cachedResponse = obtainCachedResponse(request);

			// If not, continue with the request an put the response in the cache.
			// If so, return the cached response but update the metatdata built based on
			// the request.
			if (cachedResponse.isEmpty()) {
				return cacheResponse(next.handle(request));
			}

			return createResponse(cachedResponse.get());
		}

		private ServerResponse createResponse(CachedResponse cachedResponse) {
			// FIXME implement me...
			return null;
		}

		private ServerResponse cacheResponse(ServerResponse response) {
			// FIXME implement me...
			return response;
		}

		private Optional<CachedResponse> obtainCachedResponse(ServerRequest request) {
			// FIXME implement me...
			return null;
		}

	}

	/**
	 * A {@link FilterSupplier} implementation that provides all
	 * {@link HandlerFilterFunction handler filter functions} available to be able to
	 * cache HTTP responses.
	 *
	 * @author Ingo Griebsch
	 */
	static class FilterSupplier extends SimpleFilterSupplier {

		FilterSupplier() {
			super(ResponseCacheFilterFunctions.class);
		}

	}

	/**
	 * Allows registration and access to a specific {@link ResponseCacheManager}.
	 * <p>
	 * Allows access to a {@link ResponseCacheManager} that is to be used for a specific
	 * route.
	 *
	 * @author Ingo Griebsch
	 */
	static class ResponseCacheManagerRegistry {

		void register(String id, ResponseCacheManager manager) {
			// FIXME implement me...
		}

		ResponseCacheManager get(String id) {
			// FIXME implement me...
			return null;
		}

	}

	/**
	 * Allows responses and their metadata to be cached.
	 * <p>
	 * Builds on the cache abstraction provided by Spring, enabling flexible configuration
	 * of the underlying cache.
	 *
	 * @author Ingo Griebsch
	 */
	static class ResponseCacheManager {

		private final Cache cache;

		ResponseCacheManager(Cache cache) {
			this.cache = cache;
		}

		<T> Optional<T> get(CacheKey key, Class<T> type) {
			T entry = null;
			try {
				entry = cache.get(key, type);
			}
			catch (Exception e) {
				// FIXME log
			}
			return Optional.ofNullable(entry);
		}

		void put(CacheKey key, Object object) {
			cache.put(key, object);
		}

	}

	/**
	 * Represents the metadata of a cached HTTP response.
	 *
	 * @author Ingo Griebsch
	 */
	static class CachedResponseMetadata {

		private final List<String> headers;

		CachedResponseMetadata(List<String> headers) {
			this.headers = headers;
		}

		List<String> getHeaders() {
			return headers;
		}

	}

	/**
	 * Represents a cached HTTP response.
	 *
	 * @author Ingo Griebsch
	 */
	static class CachedResponse {

		// FIXME Add the body
		private final HttpStatusCode statusCode;

		private final HttpHeaders headers;

		private final Date timestamp;

		CachedResponse(HttpStatusCode statusCode, HttpHeaders headers, Date timestamp) {
			this.statusCode = statusCode;
			this.headers = headers;
			this.timestamp = timestamp;
		}

		public HttpStatusCode getStatusCode() {
			return statusCode;
		}

		public HttpHeaders getHeaders() {
			return headers;
		}

		public Date getTimestamp() {
			return timestamp;
		}

	}

	/**
	 * A factory that allows to create {@link CacheKey cache-keys} based on a
	 * {@link ServerRequest} and additional context related information.
	 *
	 * @author Ingo Griebsch
	 */
	static class CacheKeyFactory {

		private final ThreadLocal<MessageDigest> messageDigest;

		CacheKeyFactory() {
			this.messageDigest = ThreadLocal.withInitial(() -> {
				try {
					return MessageDigest.getInstance("MD5");
				}
				catch (NoSuchAlgorithmException e) {
					throw new RuntimeException("Caught exception while creating CacheKeyCalculator!", e);
				}
			});
		}

		CacheKey from(ServerRequest request, @Nullable String prefix) {
			return from(request, List.of(), prefix);
		}

		CacheKey from(ServerRequest request, List<String> headers, @Nullable String prefix) {
			byte[] digest = messageDigest.get().digest(calculate(request, headers));
			return new CacheKey(prefix, Base64.getEncoder().encodeToString(digest));
		}

		private byte[] calculate(ServerRequest request, List<String> headers) {
			// FIXME implement me...
			return null;
		}

		static class CacheKey {

			private final String value;

			CacheKey(@Nullable String prefix, String value) {
				this.value = "%s%s".formatted(StringUtils.hasText(prefix) ? "%s_".formatted(prefix) : "", value);
			}

			String getValue() {
				return value;
			}

			@Override
			public int hashCode() {
				return Objects.hash(value);
			}

			@Override
			public boolean equals(Object obj) {
				if (this == obj) {
					return true;
				}
				if (obj == null) {
					return false;
				}
				if (getClass() != obj.getClass()) {
					return false;
				}
				CacheKey other = (CacheKey) obj;
				return Objects.equals(value, other.value);
			}

		}

	}

	/**
	 * A set of servlet related utilities to ease the implementation of the
	 * {@link ResponseCacheFilterFunctions response cache filter functions}.
	 *
	 * @author Ingo Griebsch
	 */
	abstract static class ServletUtils {

		private ServletUtils() {
		}

		static boolean shouldRevalidate(ServerRequest request) {
			return Optional.ofNullable(request.headers().asHttpHeaders().getCacheControl())
				.map(v -> v.matches(".*(\s|,|^)no-cache(\\s|,|$).*"))
				.orElse(false);
		}

		static boolean isCacheable(ServerRequest request) {
			return isGetMethod(request) && !hasBody(request) && isCacheControlAllowed(request);
		}

		static boolean isCacheControlAllowed(ServerRequest request) {
			return isCacheControlAllowed(request.headers().header(CACHE_CONTROL));
		}

		static boolean isCacheControlAllowed(ServerResponse response) {
			return isCacheControlAllowed(response.headers().get(CACHE_CONTROL));
		}

		static boolean hasBody(ServerRequest request) {
			// FIXME What if no Content-Length header is present? Should we assume that
			// the request has no body or should we read
			// the body to determine if it has content?
			return request.headers().contentLength().orElse(0L) > 0;
		}

		static boolean isGetMethod(ServerRequest request) {
			return HttpMethod.GET.equals(request.method());
		}

		static boolean isCacheable(ServerResponse response) {
			List<HttpStatus> cacheableStatusCodes = List.of(OK, PARTIAL_CONTENT, MOVED_PERMANENTLY);
			return hasStatusCode(response, cacheableStatusCodes) && isCacheControlAllowed(response)
					&& !isVaryWildcard(response);
		}

		static boolean hasStatusCode(ServerResponse response, List<HttpStatus> statusCodes) {
			return statusCodes.contains(response.statusCode());
		}

		static boolean isVaryWildcard(ServerResponse response) {
			HttpHeaders headers = response.headers();
			List<String> varyValues = headers.getOrEmpty(VARY);
			return varyValues.stream().anyMatch("*"::equals);
		}

		private static boolean isCacheControlAllowed(@Nullable List<String> headerValues) {
			if (headerValues == null) {
				return false;
			}
			return headerValues.stream().noneMatch(List.of("private", "no-store")::contains);
		}

	}

}
