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
import com.github.benmanes.caffeine.cache.Weigher;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.AllNestedConditions;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.cloud.gateway.filter.factory.cache.GlobalLocalResponseCacheGatewayFilter;
import org.springframework.cloud.gateway.filter.factory.cache.LocalResponseCacheGatewayFilterFactory;
import org.springframework.cloud.gateway.filter.factory.cache.LocalResponseCacheProperties;
import org.springframework.cloud.gateway.filter.factory.cache.ResponseCacheManagerFactory;
import org.springframework.cloud.gateway.filter.factory.cache.ResponseCacheSizeWeigher;
import org.springframework.cloud.gateway.filter.factory.cache.keygenerator.CacheKeyGenerator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;

/**
 * @author Ignacio Lozano
 * @author Marta Medio
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({ LocalResponseCacheProperties.class })
@ConditionalOnClass({ Weigher.class, Caffeine.class, CaffeineCacheManager.class })
@Conditional(LocalResponseCacheAutoConfiguration.OnGlobalLocalResponseCacheCondition.class)
public class LocalResponseCacheAutoConfiguration {

	private static final Log LOGGER = LogFactory.getLog(LocalResponseCacheAutoConfiguration.class);

	private static final String RESPONSE_CACHE_NAME = "response-cache";

	/* for testing */ static final String RESPONSE_CACHE_MANAGER_NAME = "gatewayCacheManager";

	@Bean
	public GlobalLocalResponseCacheGatewayFilter globalLocalResponseCacheGatewayFilter(
			ResponseCacheManagerFactory responseCacheManagerFactory,
			@Qualifier(RESPONSE_CACHE_MANAGER_NAME) CacheManager cacheManager,
			LocalResponseCacheProperties properties) {
		return new GlobalLocalResponseCacheGatewayFilter(responseCacheManagerFactory, responseCache(cacheManager),
				properties.getTimeToLive());
	}

	@Bean
	public LocalResponseCacheGatewayFilterFactory localResponseCacheGatewayFilterFactory(
			ResponseCacheManagerFactory responseCacheManagerFactory,
			@Qualifier(RESPONSE_CACHE_MANAGER_NAME) CacheManager cacheManager,
			LocalResponseCacheProperties properties) {
		return new LocalResponseCacheGatewayFilterFactory(responseCacheManagerFactory, responseCache(cacheManager),
				properties.getTimeToLive());
	}

	@Bean
	public ResponseCacheManagerFactory responseCacheManagerFactory(CacheKeyGenerator cacheKeyGenerator) {
		return new ResponseCacheManagerFactory(cacheKeyGenerator);
	}

	@Bean
	public CacheKeyGenerator cacheKeyGenerator() {
		return new CacheKeyGenerator();
	}

	@Bean(name = RESPONSE_CACHE_MANAGER_NAME)
	public CacheManager gatewayCacheManager(LocalResponseCacheProperties cacheProperties) {
		return createGatewayCacheManager(cacheProperties);
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	public static CaffeineCacheManager createGatewayCacheManager(LocalResponseCacheProperties cacheProperties) {
		Caffeine caffeine = Caffeine.newBuilder();
		LOGGER.info("Initializing Caffeine");
		Duration ttlSeconds = cacheProperties.getTimeToLive();
		caffeine.expireAfterWrite(ttlSeconds);

		if (cacheProperties.getSize() != null) {
			caffeine.maximumWeight(cacheProperties.getSize().toBytes()).weigher(responseCacheSizeWeigher());
		}
		CaffeineCacheManager caffeineCacheManager = new CaffeineCacheManager();
		caffeineCacheManager.setCaffeine(caffeine);
		return caffeineCacheManager;
	}

	private static ResponseCacheSizeWeigher responseCacheSizeWeigher() {
		return new ResponseCacheSizeWeigher();
	}

	Cache responseCache(CacheManager cacheManager) {
		return cacheManager.getCache(RESPONSE_CACHE_NAME);
	}

	public static class OnGlobalLocalResponseCacheCondition extends AllNestedConditions {

		OnGlobalLocalResponseCacheCondition() {
			super(ConfigurationPhase.REGISTER_BEAN);
		}

		@ConditionalOnProperty(value = "spring.cloud.gateway.enabled", havingValue = "true", matchIfMissing = true)
		static class OnGatewayPropertyEnabled {

		}

		@ConditionalOnProperty(value = "spring.cloud.gateway.filter.local-response-cache.enabled", havingValue = "true")
		static class OnLocalResponseCachePropertyEnabled {

		}

	}

}
