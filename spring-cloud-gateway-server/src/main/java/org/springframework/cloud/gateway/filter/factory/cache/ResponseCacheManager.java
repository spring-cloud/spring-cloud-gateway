/*
 * Copyright 2013-2022 the original author or authors.
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

import java.nio.ByteBuffer;
import java.time.Clock;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.springframework.cache.Cache;
import org.springframework.cloud.gateway.filter.factory.cache.keygenerator.CacheKeyGenerator;
import org.springframework.cloud.gateway.filter.factory.cache.postprocessor.AfterCacheExchangeMutator;
import org.springframework.cloud.gateway.filter.factory.cache.postprocessor.SetMaxAgeHeaderAfterCacheExchangeMutator;
import org.springframework.cloud.gateway.filter.factory.cache.postprocessor.SetResponseHeadersAfterCacheExchangeMutator;
import org.springframework.cloud.gateway.filter.factory.cache.postprocessor.SetStatusCodeAfterCacheExchangeMutator;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMessage;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.server.ServerWebExchange;

/**
 * @author Marta Medio
 * @author Ignacio Lozano
 */
public class ResponseCacheManager {

	private static final Log LOGGER = LogFactory.getLog(ResponseCacheManager.class);

	private static final List<String> forbiddenCacheControlValues = Arrays.asList("private", "no-store");

	private static final String VARY_WILDCARD = "*";

	final CacheKeyGenerator cacheKeyGenerator;

	final List<AfterCacheExchangeMutator> afterCacheExchangeMutators;

	private final Cache cache;

	public ResponseCacheManager(CacheKeyGenerator cacheKeyGenerator, Cache cache, Duration configuredTimeToLive) {
		this.cacheKeyGenerator = cacheKeyGenerator;
		this.cache = cache;
		this.afterCacheExchangeMutators = List.of(new SetResponseHeadersAfterCacheExchangeMutator(),
				new SetStatusCodeAfterCacheExchangeMutator(),
				new SetMaxAgeHeaderAfterCacheExchangeMutator(configuredTimeToLive, Clock.systemDefaultZone()));
	}

	private static final List<HttpStatusCode> statusesToCache = Arrays.asList(HttpStatus.OK, HttpStatus.PARTIAL_CONTENT,
			HttpStatus.MOVED_PERMANENTLY);

	public Optional<CachedResponse> getFromCache(ServerHttpRequest request, String metadataKey) {
		CachedResponseMetadata metadata = retrieveMetadata(metadataKey);
		String key = cacheKeyGenerator.generateKey(request,
				metadata != null ? metadata.varyOnHeaders() : Collections.emptyList());

		return getFromCache(key);
	}

	public Flux<DataBuffer> processFromUpstream(String metadataKey, ServerWebExchange exchange, Flux<DataBuffer> body) {
		final ServerHttpResponse response = exchange.getResponse();
		final CachedResponseMetadata metadata = new CachedResponseMetadata(response.getHeaders().getVary());
		final String key = resolveKey(exchange, metadata.varyOnHeaders());
		CachedResponse.Builder cachedResponseBuilder = CachedResponse.create(response.getStatusCode())
				.headers(response.getHeaders());
		CachedResponse toProcess = cachedResponseBuilder.build();
		afterCacheExchangeMutators.forEach(processor -> processor.accept(exchange, toProcess));

		// Note: `map` instead of `doOnNext
		// `doOnNext` is only for side-effect operations (like logging or emitting other
		// events). Order is not guaranteed. In some cases, the signal is not in order and
		// the object will be corrupted in cache
		return body.map(dataBuffer -> {
			ByteBuffer byteBuffer = dataBuffer.toByteBuffer().asReadOnlyBuffer();
			cachedResponseBuilder.appendToBody(byteBuffer);
			return response.bufferFactory().wrap(byteBuffer);
		}).doOnComplete(() -> {
			CachedResponse responseToCache = cachedResponseBuilder.timestamp(toProcess.timestamp()).build();
			saveMetadataInCache(metadataKey, metadata);
			saveInCache(key, responseToCache);
		});
	}

	private Optional<CachedResponse> getFromCache(String key) {
		CachedResponse cachedResponse;
		try {
			cachedResponse = cache.get(key, CachedResponse.class);
		}
		catch (RuntimeException anyException) {
			LOGGER.error("Error reading from cache. Data will not come from cache.", anyException);
			cachedResponse = null;
		}
		return Optional.ofNullable(cachedResponse);
	}

	public String resolveMetadataKey(ServerWebExchange exchange) {
		return cacheKeyGenerator.generateMetadataKey(exchange.getRequest());
	}

	public String resolveKey(ServerWebExchange exchange, List<String> varyOnHeaders) {
		return cacheKeyGenerator.generateKey(exchange.getRequest(), varyOnHeaders);
	}

	Mono<Void> processFromCache(ServerWebExchange exchange, String metadataKey, CachedResponse cachedResponse) {
		final ServerHttpResponse response = exchange.getResponse();

		afterCacheExchangeMutators.forEach(processor -> processor.accept(exchange, cachedResponse));
		saveMetadataInCache(metadataKey, new CachedResponseMetadata(cachedResponse.headers().getVary()));

		if (HttpStatus.NOT_MODIFIED.equals(response.getStatusCode())) {
			return response.writeWith(Mono.empty());
		}
		else {
			return response.writeWith(
					Flux.fromIterable(cachedResponse.body()).map(data -> response.bufferFactory().wrap(data)));
		}
	}

	private CachedResponseMetadata retrieveMetadata(String metadataKey) {
		CachedResponseMetadata metadata;
		try {
			metadata = cache.get(metadataKey, CachedResponseMetadata.class);
		}
		catch (RuntimeException anyException) {
			LOGGER.error("Error reading from cache. Metadata Data will not come from cache.", anyException);
			metadata = null;
		}
		return metadata;
	}

	boolean isResponseCacheable(ServerHttpResponse response) {
		return isStatusCodeToCache(response) && isCacheControlAllowed(response) && !isVaryWildcard(response);
	}

	private boolean isStatusCodeToCache(ServerHttpResponse response) {
		return statusesToCache.contains(response.getStatusCode());
	}

	boolean isRequestCacheable(ServerHttpRequest request) {
		return HttpMethod.GET.equals(request.getMethod()) && !hasRequestBody(request) && isCacheControlAllowed(request);
	}

	private boolean isVaryWildcard(ServerHttpResponse response) {
		HttpHeaders headers = response.getHeaders();
		List<String> varyValues = headers.getOrEmpty(HttpHeaders.VARY);

		return varyValues.stream().anyMatch(VARY_WILDCARD::equals);
	}

	private boolean isCacheControlAllowed(HttpMessage request) {
		HttpHeaders headers = request.getHeaders();
		List<String> cacheControlHeader = headers.getOrEmpty(HttpHeaders.CACHE_CONTROL);

		return cacheControlHeader.stream().noneMatch(forbiddenCacheControlValues::contains);
	}

	private static boolean hasRequestBody(ServerHttpRequest request) {
		return request.getHeaders().getContentLength() > 0;
	}

	private void saveInCache(String cacheKey, CachedResponse cachedResponse) {
		try {
			cache.put(cacheKey, cachedResponse);
		}
		catch (RuntimeException anyException) {
			LOGGER.error("Error writing into cache. Data will not be cached", anyException);
		}
	}

	private void saveMetadataInCache(String metadataKey, CachedResponseMetadata metadata) {
		try {
			cache.put(metadataKey, metadata);
		}
		catch (RuntimeException anyException) {
			LOGGER.error("Error writing into cache. Data will not be cached", anyException);
		}
	}

}
