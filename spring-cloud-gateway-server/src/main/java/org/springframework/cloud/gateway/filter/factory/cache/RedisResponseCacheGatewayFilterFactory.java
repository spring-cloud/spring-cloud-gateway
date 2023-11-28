/*
 * Copyright 2023-2023 the original author or authors.
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

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cache.Cache;
import org.springframework.cloud.gateway.config.RedisResponseCacheAutoConfiguration;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.GatewayFilterFactory;
import org.springframework.cloud.gateway.support.HasRouteId;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.validation.annotation.Validated;

@ConditionalOnProperty(value = "spring.cloud.gateway.filter.redis-response-cache.enabled", havingValue = "true")
public class RedisResponseCacheGatewayFilterFactory
		extends ResponseCacheGatewayFilterFactory<RedisResponseCacheGatewayFilterFactory.RouteCacheConfiguration>
		implements GatewayFilterFactory<RedisResponseCacheGatewayFilterFactory.RouteCacheConfiguration> {

	/**
	 * Exchange attribute name to track if the request has been already process by cache
	 * at route filter level.
	 */
	public static final String REDIS_RESPONSE_CACHE_FILTER_APPLIED = "RedisResponseCacheGatewayFilter-Applied";

	private RedisConnectionFactory redisConnectionFactory;

	public RedisResponseCacheGatewayFilterFactory(ResponseCacheManagerFactory cacheManagerFactory,
			Duration defaultTimeToLive, RedisConnectionFactory redisConnectionFactory) {
		super(RouteCacheConfiguration.class);
		this.cacheManagerFactory = cacheManagerFactory;
		this.defaultTimeToLive = defaultTimeToLive;
		this.redisConnectionFactory = redisConnectionFactory;
	}

	@Override
	public List<String> shortcutFieldOrder() {
		return List.of("timeToLive");
	}

	@Override
	public GatewayFilter apply(RouteCacheConfiguration config) {
		RedisResponseCacheProperties cacheProperties = mapRouteCacheConfig(config);

		Cache routeCache = RedisResponseCacheAutoConfiguration
				.createGatewayCacheManager(cacheProperties, redisConnectionFactory)
				.getCache(config.getRouteId() + "-cache");
		return new ResponseCacheGatewayFilter(cacheManagerFactory.create(routeCache, cacheProperties.getTimeToLive()),
				REDIS_RESPONSE_CACHE_FILTER_APPLIED);
	}

	private RedisResponseCacheProperties mapRouteCacheConfig(RouteCacheConfiguration config) {
		Duration timeToLive = config.getTimeToLive() != null ? config.getTimeToLive() : defaultTimeToLive;

		RedisResponseCacheProperties responseCacheProperties = new RedisResponseCacheProperties();
		responseCacheProperties.setTimeToLive(timeToLive);

		return responseCacheProperties;
	}

	@Validated
	public static class RouteCacheConfiguration implements HasRouteId {

		private Duration timeToLive;

		private String routeId;

		public Duration getTimeToLive() {
			return timeToLive;
		}

		public RouteCacheConfiguration setTimeToLive(Duration timeToLive) {
			this.timeToLive = timeToLive;
			return this;
		}

		@Override
		public void setRouteId(String routeId) {
			this.routeId = routeId;
		}

		@Override
		public String getRouteId() {
			return this.routeId;
		}

	}

}
