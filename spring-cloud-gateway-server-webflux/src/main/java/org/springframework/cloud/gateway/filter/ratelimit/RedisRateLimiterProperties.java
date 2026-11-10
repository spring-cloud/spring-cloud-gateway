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

package org.springframework.cloud.gateway.filter.ratelimit;

import java.util.HashMap;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.gateway.config.GatewayProperties;
import org.springframework.core.style.ToStringCreator;

/**
 * Externalized configuration for {@link RedisRateLimiter}. Extracting the bound
 * properties into a dedicated class with a default constructor lets Spring Cloud's
 * {@code ConfigurationPropertiesRebinder} rebind them on a refresh (including resetting
 * removed properties to their defaults), which is not possible while the properties live
 * on the constructor-injected {@link RedisRateLimiter} bean.
 *
 * @author Aryamann Singh
 */
@ConfigurationProperties(GatewayProperties.PREFIX + ".redis-rate-limiter")
public class RedisRateLimiterProperties {

	/**
	 * Whether or not to include headers containing rate limiter information, defaults to
	 * true.
	 */
	private boolean includeHeaders = true;

	/**
	 * The name of the header that returns number of remaining requests during the current
	 * second.
	 */
	private String remainingHeader = RedisRateLimiter.REMAINING_HEADER;

	/** The name of the header that returns the replenish rate configuration. */
	private String replenishRateHeader = RedisRateLimiter.REPLENISH_RATE_HEADER;

	/** The name of the header that returns the burst capacity configuration. */
	private String burstCapacityHeader = RedisRateLimiter.BURST_CAPACITY_HEADER;

	/** The name of the header that returns the requested tokens configuration. */
	private String requestedTokensHeader = RedisRateLimiter.REQUESTED_TOKENS_HEADER;

	/**
	 * Per-route rate limiter configuration, keyed by route id. This map backs
	 * {@link RedisRateLimiter#getConfig()} so that {@code redis-rate-limiter.config.*}
	 * keeps binding as it did when the properties lived on the rate limiter itself.
	 */
	private Map<String, RedisRateLimiter.Config> config = new HashMap<>();

	public boolean isIncludeHeaders() {
		return includeHeaders;
	}

	public void setIncludeHeaders(boolean includeHeaders) {
		this.includeHeaders = includeHeaders;
	}

	public String getRemainingHeader() {
		return remainingHeader;
	}

	public void setRemainingHeader(String remainingHeader) {
		this.remainingHeader = remainingHeader;
	}

	public String getReplenishRateHeader() {
		return replenishRateHeader;
	}

	public void setReplenishRateHeader(String replenishRateHeader) {
		this.replenishRateHeader = replenishRateHeader;
	}

	public String getBurstCapacityHeader() {
		return burstCapacityHeader;
	}

	public void setBurstCapacityHeader(String burstCapacityHeader) {
		this.burstCapacityHeader = burstCapacityHeader;
	}

	public String getRequestedTokensHeader() {
		return requestedTokensHeader;
	}

	public void setRequestedTokensHeader(String requestedTokensHeader) {
		this.requestedTokensHeader = requestedTokensHeader;
	}

	public Map<String, RedisRateLimiter.Config> getConfig() {
		return config;
	}

	public void setConfig(Map<String, RedisRateLimiter.Config> config) {
		this.config = config;
	}

	@Override
	public String toString() {
		return new ToStringCreator(this).append("includeHeaders", includeHeaders)
			.append("remainingHeader", remainingHeader)
			.append("replenishRateHeader", replenishRateHeader)
			.append("burstCapacityHeader", burstCapacityHeader)
			.append("requestedTokensHeader", requestedTokensHeader)
			.append("config", config)
			.toString();
	}

}
