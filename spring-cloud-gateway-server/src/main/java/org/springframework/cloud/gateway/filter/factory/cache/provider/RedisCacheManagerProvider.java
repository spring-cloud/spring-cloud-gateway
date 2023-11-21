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

package org.springframework.cloud.gateway.filter.factory.cache.provider;

import org.springframework.cache.CacheManager;
import org.springframework.cloud.gateway.filter.factory.cache.LocalResponseCacheProperties;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;

public class RedisCacheManagerProvider implements CacheManagerProvider {

	private final RedisConnectionFactory redisConnectionFactory;

	public RedisCacheManagerProvider(RedisConnectionFactory redisConnectionFactory) {
		this.redisConnectionFactory = redisConnectionFactory;
	}

	@Override
	public CacheManager getCacheManager(LocalResponseCacheProperties localResponseCacheProperties) {
		RedisCacheConfiguration redisCacheConfigurationWithTtl = RedisCacheConfiguration.defaultCacheConfig()
				.entryTtl(localResponseCacheProperties.getTimeToLive());

		return RedisCacheManager.builder(redisConnectionFactory).cacheDefaults(redisCacheConfigurationWithTtl).build();
	}

}
