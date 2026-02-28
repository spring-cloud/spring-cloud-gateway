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

package org.springframework.cloud.gateway.config;

import java.util.Collections;

import com.github.benmanes.caffeine.cache.Caffeine;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.cache.CaffeineCacheMetrics;

import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.micrometer.metrics.autoconfigure.CompositeMeterRegistryAutoConfiguration;
import org.springframework.boot.micrometer.metrics.autoconfigure.MetricsAutoConfiguration;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.cloud.gateway.filter.factory.cache.LocalResponseCacheGatewayFilterFactory.CacheMetricsListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Auto-configuration for LocalResponseCache metrics. Registers Caffeine cache metrics
 * with the {@link MeterRegistry} when both the cache infrastructure and Micrometer are
 * available.
 *
 * @author Eisen
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnClass({ Caffeine.class, CaffeineCacheManager.class, MeterRegistry.class, MetricsAutoConfiguration.class })
@ConditionalOnBean(MeterRegistry.class)
@ConditionalOnProperty(name = GatewayProperties.PREFIX + ".metrics.enabled", matchIfMissing = true)
@AutoConfigureAfter({ LocalResponseCacheAutoConfiguration.class, MetricsAutoConfiguration.class,
		CompositeMeterRegistryAutoConfiguration.class })
public class LocalResponseCacheMetricsAutoConfiguration {

	@Bean
	CacheMetricsListener localResponseCacheMetricsListener(MeterRegistry meterRegistry) {
		return (cache, cacheName) -> CaffeineCacheMetrics.monitor(meterRegistry, cache, cacheName,
				Collections.emptyList());
	}

}
