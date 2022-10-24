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

package org.springframework.cloud.gateway.config;

import java.time.Duration;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.cloud.gateway.config.conditional.ConditionalOnEnabledFilter;
import org.springframework.cloud.gateway.filter.factory.cache.LocalResponseCacheGatewayFilterFactory;
import org.springframework.cloud.gateway.filter.factory.cache.LocalResponseCacheProperties;
import org.springframework.cloud.gateway.filter.factory.cache.ResponseCacheManagerFactory;
import org.springframework.cloud.gateway.filter.factory.cache.ResponseCacheSizeWeigher;
import org.springframework.cloud.gateway.filter.factory.cache.keygenerator.CacheKeyGenerator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.unit.DataSize;

/**
 * @author Ignacio Lozano
 */
@Configuration
@EnableConfigurationProperties({LocalResponseCacheProperties.class})
@ConditionalOnProperty(name = "spring.cloud.gateway.enabled", matchIfMissing = true)
@ConditionalOnEnabledFilter(LocalResponseCacheGatewayFilterFactory.class)
public class LocalResponseCacheAutoConfiguration {

	private static final Logger LOGGER = LoggerFactory.getLogger(LocalResponseCacheAutoConfiguration.class);

	private static final String RESPONSE_CACHE_NAME = "response-cache";

	@Bean
	public LocalResponseCacheGatewayFilterFactory localResponseCacheGatewayFilterFactory(CacheManager cacheManager,
																						 LocalResponseCacheProperties properties) {
		ResponseCacheManagerFactory responseCacheManagerFactory = new ResponseCacheManagerFactory(
				new CacheKeyGenerator());
		return new LocalResponseCacheGatewayFilterFactory(responseCacheManagerFactory, responseCache(cacheManager),
				configuredTimeToLive(properties));
	}

	@Bean
	public CacheKeyGenerator cacheKeyGenerator() {
		return new CacheKeyGenerator();
	}

	@Bean
	public CacheManager concurrentMapCacheManager(LocalResponseCacheProperties cacheProperties) {
		Caffeine caffeine = Caffeine.newBuilder();
		LOGGER.info("Initializing Caffeine");
		Duration ttlSeconds = getTtlSecondsConfiguration(cacheProperties);
		caffeine.expireAfterWrite(ttlSeconds);

		Long maxSizeBytes = getSizeBytesConfiguration(cacheProperties);
		if (maxSizeBytes != null) {
			caffeine.maximumWeight(maxSizeBytes).weigher(new ResponseCacheSizeWeigher());
		}

		CaffeineCacheManager caffeineCacheManager = new CaffeineCacheManager();
		caffeineCacheManager.setCaffeine(caffeine);
		return caffeineCacheManager;
	}

	@Bean
	Cache responseCache(CacheManager cacheManager) {
		return cacheManager.getCache(RESPONSE_CACHE_NAME);
	}

	@Bean
	Duration configuredTimeToLive(LocalResponseCacheProperties cacheProperties) {
		return getTtlSecondsConfiguration(cacheProperties);
	}

	private Long getSizeBytesConfiguration(LocalResponseCacheProperties cacheProperties) {
		if (cacheProperties.getSize() != null) {
			return DataSize.parse(cacheProperties.getSize()).toBytes();
		}
		else {
			return null;
		}
	}

	private Duration getTtlSecondsConfiguration(LocalResponseCacheProperties cacheProperties) {
		return cacheProperties.getTimeToLive();
	}

}
