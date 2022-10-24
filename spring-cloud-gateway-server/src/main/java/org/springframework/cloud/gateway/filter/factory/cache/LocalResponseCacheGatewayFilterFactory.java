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
import java.util.List;
import java.util.Objects;

import org.springframework.cache.Cache;
import org.springframework.cloud.gateway.config.LocalResponseCacheAutoConfiguration;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.cloud.gateway.support.HasRouteId;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;

/**
 * @author Marta Medio
 * @author Ignacio Lozano
 */
public class LocalResponseCacheGatewayFilterFactory
		extends AbstractGatewayFilterFactory<LocalResponseCacheGatewayFilterFactory.RouteCacheConfiguration> {

	private final Cache globalCache;

	ResponseCacheManagerFactory managerFactory;

	Duration configuredTimeToLive;

	public LocalResponseCacheGatewayFilterFactory(ResponseCacheManagerFactory managerFactory, Cache globalCache,
												  Duration configuredTimeToLive) {
		super(RouteCacheConfiguration.class);
		this.managerFactory = managerFactory;
		this.globalCache = globalCache;
		this.configuredTimeToLive = configuredTimeToLive;
	}

	@Override
	public GatewayFilter apply(RouteCacheConfiguration config) {
		LocalResponseCacheProperties cacheProperties = mapRouteCacheConfig(config);

		if (shouldUseGlobalCacheConfiguration(config)) {
			return new ResponseCacheGatewayFilter(managerFactory.create(globalCache, configuredTimeToLive));
		}
		else {
			Cache routeCache = new LocalResponseCacheAutoConfiguration().concurrentMapCacheManager(cacheProperties)
																		.getCache(config.getRouteId() + "-cache");
			return new ResponseCacheGatewayFilter(managerFactory.create(routeCache, cacheProperties.getTimeToLive()));
		}
	}

	private boolean shouldUseGlobalCacheConfiguration(RouteCacheConfiguration config) {
		return Objects.isNull(config.getTimeToLive()) && !StringUtils.hasText(config.getSize());
	}

	private LocalResponseCacheProperties mapRouteCacheConfig(RouteCacheConfiguration config) {
		LocalResponseCacheProperties responseCacheProperties = new LocalResponseCacheProperties();
		responseCacheProperties.setSize(config.getSize());
		responseCacheProperties.setTimeToLive(config.getTimeToLive());
		return responseCacheProperties;
	}

	@Override
	public List<String> shortcutFieldOrder() {
		return List.of("timeToLive", "size");
	}

	@Validated
	public static class RouteCacheConfiguration implements HasRouteId {

		private String size;

		private Duration timeToLive;

		private String routeId;

		public String getSize() {
			return size;
		}

		public RouteCacheConfiguration setSize(String size) {
			this.size = size;
			return this;
		}

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
