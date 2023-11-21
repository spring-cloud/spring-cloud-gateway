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

import java.time.Duration;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.cloud.gateway.filter.factory.cache.LocalResponseCacheProperties;
import org.springframework.util.unit.DataSize;

class CaffieneCacheManagerProviderTest {

	@Test
	void providedCacheManagerIsCaffieneCacheManager() {
		LocalResponseCacheProperties localResponseCacheProperties = new LocalResponseCacheProperties();
		localResponseCacheProperties.setSize(DataSize.ofKilobytes(6789));
		localResponseCacheProperties.setTimeToLive(Duration.ofSeconds(99));

		CaffieneCacheManagerProvider caffieneCacheManagerProvider = new CaffieneCacheManagerProvider();
		CacheManager cacheManager = caffieneCacheManagerProvider.getCacheManager(localResponseCacheProperties);

		Assertions.assertInstanceOf(CaffeineCacheManager.class, cacheManager);
	}

}
