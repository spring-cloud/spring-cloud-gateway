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

package org.springframework.cloud.gateway.filter.factory.cache;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.jspecify.annotations.Nullable;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cache.Cache;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.cloud.gateway.filter.factory.cache.LocalResponseCacheProperties.RequestOptions;
import org.springframework.cloud.gateway.support.HasRouteId;
import org.springframework.util.unit.DataSize;
import org.springframework.validation.annotation.Validated;

/**
 * {@link org.springframework.cloud.gateway.filter.factory.GatewayFilterFactory} of
 * {@link ResponseCacheGatewayFilter}. By default, a global cache (defined as properties
 * in the application) is used. For specific route configuration, parameters can be added
 * following {@link RouteCacheConfiguration} class.
 *
 * @author Marta Medio
 * @author Ignacio Lozano
 */
@ConditionalOnProperty(value = "spring.cloud.gateway.server.webflux.filter.local-response-cache.enabled",
		havingValue = "true")
public class LocalResponseCacheGatewayFilterFactory
		extends AbstractGatewayFilterFactory<LocalResponseCacheGatewayFilterFactory.RouteCacheConfiguration> {

	/**
	 * Exchange attribute name to track if the request has been already process by cache
	 * at route filter level.
	 */
	public static final String LOCAL_RESPONSE_CACHE_FILTER_APPLIED = "LocalResponseCacheGatewayFilter-Applied";

	private final ResponseCacheManagerFactory cacheManagerFactory;

	private final Duration defaultTimeToLive;

	private final DataSize defaultSize;

	private final RequestOptions requestOptions;

	private final CaffeineCacheManager caffeineCacheManager;

	private final Map<String, CacheSettings> registeredCacheSettings = new ConcurrentHashMap<>();

	public LocalResponseCacheGatewayFilterFactory(ResponseCacheManagerFactory cacheManagerFactory,
			Duration defaultTimeToLive, DataSize defaultSize, RequestOptions requestOptions) {
		this(cacheManagerFactory, defaultTimeToLive, defaultSize, requestOptions, new CaffeineCacheManager());
	}

	public LocalResponseCacheGatewayFilterFactory(ResponseCacheManagerFactory cacheManagerFactory,
			Duration defaultTimeToLive, DataSize defaultSize, RequestOptions requestOptions,
			CaffeineCacheManager caffeineCacheManager) {
		super(RouteCacheConfiguration.class);
		this.cacheManagerFactory = cacheManagerFactory;
		this.defaultTimeToLive = defaultTimeToLive;
		this.defaultSize = defaultSize;
		this.requestOptions = requestOptions;
		this.caffeineCacheManager = caffeineCacheManager;
	}

	@Override
	@SuppressWarnings({ "unchecked", "rawtypes" })
	public GatewayFilter apply(RouteCacheConfiguration config) {
		LocalResponseCacheProperties cacheProperties = mapRouteCacheConfig(config);

		Cache routeCache = registerOrReuseCache(config, cacheProperties);
		return new ResponseCacheGatewayFilter(
				cacheManagerFactory.create(routeCache, cacheProperties.getTimeToLive(), requestOptions));

	}

	/**
	 * Returns the cache backing the route. A route refresh applies the filter again, so
	 * the cache already registered is reused when the route cache configuration has not
	 * changed, keeping the entries cached so far. A new cache is built when the
	 * configuration changed, and also when the route has no id, because in that case the
	 * cache name cannot tell one route from another.
	 */
	@SuppressWarnings({ "unchecked", "rawtypes" })
	private Cache registerOrReuseCache(RouteCacheConfiguration config, LocalResponseCacheProperties cacheProperties) {
		String cacheName = config.getRouteId() + "-cache";
		CacheSettings settings = new CacheSettings(cacheProperties.getTimeToLive(), cacheProperties.getSize());
		if (config.getRouteId() == null || !settings.equals(registeredCacheSettings.get(cacheName))) {
			Caffeine caffeine = LocalResponseCacheUtils.createCaffeine(cacheProperties);
			caffeineCacheManager.registerCustomCache(cacheName, caffeine.build());
			registeredCacheSettings.put(cacheName, settings);
		}
		Cache routeCache = caffeineCacheManager.getCache(cacheName);
		Objects.requireNonNull(routeCache, "Cache " + cacheName + " not found");
		return routeCache;
	}

	private LocalResponseCacheProperties mapRouteCacheConfig(RouteCacheConfiguration config) {
		Duration timeToLive = config.getTimeToLive() != null ? config.getTimeToLive() : defaultTimeToLive;
		DataSize size = config.getSize() != null ? config.getSize() : defaultSize;

		LocalResponseCacheProperties responseCacheProperties = new LocalResponseCacheProperties();
		responseCacheProperties.setTimeToLive(timeToLive);
		responseCacheProperties.setSize(size);
		return responseCacheProperties;
	}

	@Override
	public List<String> shortcutFieldOrder() {
		return List.of("timeToLive", "size");
	}

	@Validated
	public static class RouteCacheConfiguration implements HasRouteId {

		private @Nullable DataSize size;

		private @Nullable Duration timeToLive;

		private @Nullable String routeId;

		public @Nullable DataSize getSize() {
			return size;
		}

		public RouteCacheConfiguration setSize(@Nullable DataSize size) {
			this.size = size;
			return this;
		}

		public @Nullable Duration getTimeToLive() {
			return timeToLive;
		}

		public RouteCacheConfiguration setTimeToLive(@Nullable Duration timeToLive) {
			this.timeToLive = timeToLive;
			return this;
		}

		@Override
		public void setRouteId(String routeId) {
			this.routeId = routeId;
		}

		@Override
		public @Nullable String getRouteId() {
			return this.routeId;
		}

	}

	private record CacheSettings(@Nullable Duration timeToLive, @Nullable DataSize size) {
	}

}
