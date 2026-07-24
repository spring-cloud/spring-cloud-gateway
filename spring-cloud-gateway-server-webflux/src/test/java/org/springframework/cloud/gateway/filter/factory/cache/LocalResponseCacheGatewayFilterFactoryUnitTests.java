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

import org.junit.jupiter.api.Test;

import org.springframework.cache.Cache;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.cloud.gateway.filter.factory.cache.LocalResponseCacheGatewayFilterFactory.RouteCacheConfiguration;
import org.springframework.cloud.gateway.filter.factory.cache.keygenerator.CacheKeyGenerator;
import org.springframework.util.unit.DataSize;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link LocalResponseCacheGatewayFilterFactory}.
 */
public class LocalResponseCacheGatewayFilterFactoryUnitTests {

	@Test
	void applyReusesRegisteredCacheOnRouteRefresh() {
		CaffeineCacheManager cacheManager = new CaffeineCacheManager();
		LocalResponseCacheGatewayFilterFactory factory = createFactory(cacheManager);

		RouteCacheConfiguration config = new RouteCacheConfiguration();
		config.setRouteId("refreshed-route");

		factory.apply(config);
		Cache cache = cacheManager.getCache("refreshed-route-cache");
		assertThat(cache).isNotNull();
		cache.put("cached-key", "cached-value");

		factory.apply(config);

		Cache cacheAfterRefresh = cacheManager.getCache("refreshed-route-cache");
		assertThat(cacheAfterRefresh).isSameAs(cache);
		assertThat(cacheAfterRefresh.get("cached-key", String.class)).isEqualTo("cached-value");
	}

	@Test
	void applyRegistersOneCachePerRoute() {
		CaffeineCacheManager cacheManager = new CaffeineCacheManager();
		LocalResponseCacheGatewayFilterFactory factory = createFactory(cacheManager);

		RouteCacheConfiguration firstConfig = new RouteCacheConfiguration();
		firstConfig.setRouteId("first-route");
		RouteCacheConfiguration secondConfig = new RouteCacheConfiguration();
		secondConfig.setRouteId("second-route");

		factory.apply(firstConfig);
		factory.apply(secondConfig);

		Cache firstCache = cacheManager.getCache("first-route-cache");
		Cache secondCache = cacheManager.getCache("second-route-cache");
		assertThat(firstCache).isNotNull();
		assertThat(secondCache).isNotNull();
		assertThat(firstCache).isNotSameAs(secondCache);
	}

	private LocalResponseCacheGatewayFilterFactory createFactory(CaffeineCacheManager cacheManager) {
		return new LocalResponseCacheGatewayFilterFactory(new ResponseCacheManagerFactory(new CacheKeyGenerator()),
				Duration.ofMinutes(5), DataSize.ofMegabytes(5), new LocalResponseCacheProperties().getRequest(),
				cacheManager);
	}

}
