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

import java.time.Duration;

import com.github.benmanes.caffeine.cache.Caffeine;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.cloud.gateway.filter.factory.cache.LocalResponseCacheGatewayFilterFactory.CacheMetricsListener;
import org.springframework.cloud.gateway.filter.factory.cache.LocalResponseCacheProperties;
import org.springframework.cloud.gateway.filter.factory.cache.LocalResponseCacheUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

class LocalResponseCacheMetricsAutoConfigurationTests {

	@Test
	void metricsListenerCreatedWhenMeterRegistryPresent() {
		new ApplicationContextRunner()
			.withConfiguration(AutoConfigurations.of(LocalResponseCacheAutoConfiguration.class,
					LocalResponseCacheMetricsAutoConfiguration.class))
			.withUserConfiguration(MeterRegistryConfig.class)
			.withPropertyValues(GatewayProperties.PREFIX + ".filter.local-response-cache.enabled=true")
			.run(context -> {
				assertThat(context).hasSingleBean(CacheMetricsListener.class);
			});
	}

	@Test
	void metricsListenerNotCreatedWhenMeterRegistryAbsent() {
		new ApplicationContextRunner()
			.withConfiguration(AutoConfigurations.of(LocalResponseCacheAutoConfiguration.class,
					LocalResponseCacheMetricsAutoConfiguration.class))
			.withPropertyValues(GatewayProperties.PREFIX + ".filter.local-response-cache.enabled=true")
			.run(context -> {
				assertThat(context).doesNotHaveBean(CacheMetricsListener.class);
			});
	}

	@Test
	void metricsListenerNotCreatedWhenMetricsDisabled() {
		new ApplicationContextRunner()
			.withConfiguration(AutoConfigurations.of(LocalResponseCacheAutoConfiguration.class,
					LocalResponseCacheMetricsAutoConfiguration.class))
			.withUserConfiguration(MeterRegistryConfig.class)
			.withPropertyValues(GatewayProperties.PREFIX + ".filter.local-response-cache.enabled=true",
					GatewayProperties.PREFIX + ".metrics.enabled=false")
			.run(context -> {
				assertThat(context).doesNotHaveBean(CacheMetricsListener.class);
			});
	}

	@Test
	void caffeineRecordStatsEnabled() {
		LocalResponseCacheProperties properties = new LocalResponseCacheProperties();
		properties.setTimeToLive(Duration.ofMinutes(5));
		Caffeine<Object, Object> caffeine = LocalResponseCacheUtils.createCaffeine(properties);
		com.github.benmanes.caffeine.cache.Cache<Object, Object> cache = caffeine.build();

		cache.put("key", "value");
		cache.getIfPresent("key");
		cache.getIfPresent("missing");

		assertThat(cache.stats().hitCount()).isEqualTo(1);
		assertThat(cache.stats().missCount()).isEqualTo(1);
	}

	@Test
	void cacheMetricsListenerBindsToMeterRegistry() {
		SimpleMeterRegistry registry = new SimpleMeterRegistry();
		LocalResponseCacheProperties properties = new LocalResponseCacheProperties();
		properties.setTimeToLive(Duration.ofMinutes(5));
		Caffeine<Object, Object> caffeine = LocalResponseCacheUtils.createCaffeine(properties);
		com.github.benmanes.caffeine.cache.Cache<Object, Object> cache = caffeine.build();

		new ApplicationContextRunner()
			.withConfiguration(AutoConfigurations.of(LocalResponseCacheMetricsAutoConfiguration.class))
			.withBean(MeterRegistry.class, () -> registry)
			.run(context -> {
				CacheMetricsListener listener = context.getBean(CacheMetricsListener.class);
				listener.onCacheCreated(cache, "test-cache");

				cache.put("key", "value");
				cache.getIfPresent("key");

				assertThat(registry.find("cache.gets").tag("result", "hit").functionCounter()).isNotNull();
				assertThat(registry.find("cache.size").tag("cache", "test-cache").gauge()).isNotNull();
			});
	}

	@Configuration(proxyBeanMethods = false)
	static class MeterRegistryConfig {

		@Bean
		MeterRegistry meterRegistry() {
			return new SimpleMeterRegistry();
		}

	}

}
