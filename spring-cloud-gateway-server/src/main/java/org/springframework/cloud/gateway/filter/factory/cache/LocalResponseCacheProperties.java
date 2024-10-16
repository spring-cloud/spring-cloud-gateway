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

	private static final Duration DEFAULT_CACHE_TTL_MINUTES = Duration.ofMinutes(5);

	private DataSize size;

	private Duration timeToLive;

	private RequestOptions request = new RequestOptions();

	public DataSize getSize() {
		return size;
	}

	public void setSize(DataSize size) {
		this.size = size;
	}

	public Duration getTimeToLive() {
		if (timeToLive == null) {
			LOGGER.debug(String.format(
					"No TTL configuration found. Default TTL will be applied for cache entries: %s minutes",
					DEFAULT_CACHE_TTL_MINUTES));
			return DEFAULT_CACHE_TTL_MINUTES;
		}
		else {
			return timeToLive;
		}
	}

	public void setTimeToLive(Duration timeToLive) {
		this.timeToLive = timeToLive;
	}

	public RequestOptions getRequest() {
		return request;
	}

	public void setRequest(RequestOptions request) {
		this.request = request;
	}

	@Override
	public String toString() {
		return "LocalResponseCacheProperties{" + "size=" + size + ", timeToLive=" + timeToLive + ", request=" + request
				+ '}';
	}

	public static class RequestOptions {

		private NoCacheStrategy noCacheStrategy = NoCacheStrategy.SKIP_UPDATE_CACHE_ENTRY;

		public NoCacheStrategy getNoCacheStrategy() {
			return noCacheStrategy;
		}

		public void setNoCacheStrategy(NoCacheStrategy noCacheStrategy) {
			this.noCacheStrategy = noCacheStrategy;
		}

		@Override
		public String toString() {
			return "RequestOptions{" + "noCacheStrategy=" + noCacheStrategy + '}';
		}

	}

	/**
	 * When client sends "no-cache" directive in "Cache-Control" header, the response
	 * should be re-validated from upstream. There are several strategies that indicates
	 * what to do with the new fresh response.
	 */
	public enum NoCacheStrategy {

		/**
		 * Update the cache entry by the fresh response coming from upstream with a new
		 * time to live.
		 */
		UPDATE_CACHE_ENTRY,
		/**
		 * Skip the update. The client will receive the fresh response, other clients will
		 * receive the old entry in cache.
		 */
		SKIP_UPDATE_CACHE_ENTRY

	}

}
