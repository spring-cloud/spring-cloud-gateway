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

import org.junit.jupiter.api.Test;

import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.cloud.gateway.filter.factory.cache.GlobalLocalResponseCacheGatewayFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

public class LocalResponseCacheAutoConfigurationTests {

	@Test
	void onlyOneCacheManagerBeanCreated() {
		new ApplicationContextRunner()
				.withConfiguration(AutoConfigurations.of(LocalResponseCacheAutoConfiguration.class))
				.withPropertyValues("spring.cloud.gateway.filter.local-response-cache.enabled=true").run(context -> {
					context.containsBean(LocalResponseCacheAutoConfiguration.RESPONSE_CACHE_MANAGER_NAME);
					context.assertThat().hasSingleBean(GlobalLocalResponseCacheGatewayFilter.class);
				});
	}

	@Test
	void twoCacheManagerBeans() {
		new ApplicationContextRunner()
				.withConfiguration(AutoConfigurations.of(CustomCacheManagerConfig.class,
						LocalResponseCacheAutoConfiguration.class))
				.withPropertyValues("spring.cloud.gateway.filter.local-response-cache.enabled=true").run(context -> {
					context.containsBean(LocalResponseCacheAutoConfiguration.RESPONSE_CACHE_MANAGER_NAME);
					context.containsBean("myCacheManager");
					context.assertThat().hasSingleBean(GlobalLocalResponseCacheGatewayFilter.class);
				});
	}

	@Configuration(proxyBeanMethods = false)
	static class CustomCacheManagerConfig {

		@Bean
		@Primary
		CacheManager myCacheManager() {
			return new CaffeineCacheManager();
		}

		@Bean
		Object myCacheConsumer(CacheManager cacheManager) {
			return "";
		}

	}

}
