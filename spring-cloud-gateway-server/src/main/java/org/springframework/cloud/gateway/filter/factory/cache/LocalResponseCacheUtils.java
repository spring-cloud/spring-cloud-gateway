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

import com.github.benmanes.caffeine.cache.Caffeine;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.util.StringUtils;

public final class LocalResponseCacheUtils {

	private static final Log LOGGER = LogFactory.getLog(LocalResponseCacheUtils.class);

	private LocalResponseCacheUtils() {
	}

	public static boolean isNoCacheRequest(ServerHttpRequest request) {
		String cacheControl = request.getHeaders().getCacheControl();
		return StringUtils.hasText(cacheControl) && cacheControl.matches(".*(\s|,|^)no-cache(\\s|,|$).*");
	}

	public static CaffeineCacheManager createGatewayCacheManager(LocalResponseCacheProperties cacheProperties) {
		Caffeine caffeine = createCaffeine(cacheProperties);
		CaffeineCacheManager caffeineCacheManager = new CaffeineCacheManager();
		caffeineCacheManager.setCaffeine(caffeine);
		return caffeineCacheManager;
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	public static Caffeine createCaffeine(LocalResponseCacheProperties cacheProperties) {
		Caffeine caffeine = Caffeine.newBuilder();
		LOGGER.info("Initializing Caffeine");
		Duration ttlSeconds = cacheProperties.getTimeToLive();
		caffeine.expireAfterWrite(ttlSeconds);

		if (cacheProperties.getSize() != null) {
			caffeine.maximumWeight(cacheProperties.getSize().toBytes()).weigher(new ResponseCacheSizeWeigher());
		}
		return caffeine;
	}

}
