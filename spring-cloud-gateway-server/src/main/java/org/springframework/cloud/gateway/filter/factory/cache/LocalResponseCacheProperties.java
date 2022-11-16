/*
 * Copyright 2013-2022 the original author or authors.
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

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

/**
 * @author Ignacio Lozano
 */
@ConfigurationProperties(prefix = LocalResponseCacheProperties.PREFIX)
public class LocalResponseCacheProperties {

	static final String PREFIX = "spring.cloud.gateway.filter.local-response-cache";

	private static final Log LOGGER = LogFactory.getLog(LocalResponseCacheProperties.class);

	private static final Duration DEFAULT_CACHE_TTL_SECONDS = Duration.ofMinutes(5);

	private DataSize size;

	private Duration timeToLive;

	public DataSize getSize() {
		return size;
	}

	public void setSize(DataSize size) {
		this.size = size;
	}

	public Duration getTimeToLive() {
		if (timeToLive == null) {
			LOGGER.debug(String.format(
					"No TTL configuration found. Default TTL will be applied for cache entries: %s seconds",
					DEFAULT_CACHE_TTL_SECONDS));
			return DEFAULT_CACHE_TTL_SECONDS;
		}
		else {
			return timeToLive;
		}
	}

	public void setTimeToLive(Duration timeToLive) {
		this.timeToLive = timeToLive;
	}

	@Override
	public String toString() {
		return "LocalResponseCacheProperties{" + "timeToLive=" + getTimeToLive() + '\'' + ", size='" + getSize() + '}';
	}

}
