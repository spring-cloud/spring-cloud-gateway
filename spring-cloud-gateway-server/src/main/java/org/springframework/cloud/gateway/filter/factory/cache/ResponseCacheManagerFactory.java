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

import org.springframework.cache.Cache;
import org.springframework.cloud.gateway.filter.factory.cache.keygenerator.CacheKeyGenerator;

/**
 * @author Marta Medio
 * @author Ignacio Lozano
 */
public class ResponseCacheManagerFactory {

	private final CacheKeyGenerator cacheKeyGenerator;

	public ResponseCacheManagerFactory(CacheKeyGenerator cacheKeyGenerator) {
		this.cacheKeyGenerator = cacheKeyGenerator;
	}

	public ResponseCacheManager create(Cache cache, Duration timeToLive) {
		return new ResponseCacheManager(cacheKeyGenerator, cache, timeToLive);
	}

}
