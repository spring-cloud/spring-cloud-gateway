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

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Aryamann Singh
 */
public class RedisRateLimiterPropertiesTests {

	@Test
	public void defaultsMatchHeaderConstants() {
		RedisRateLimiterProperties properties = new RedisRateLimiterProperties();

		assertThat(properties.isIncludeHeaders()).isTrue();
		assertThat(properties.getRemainingHeader()).isEqualTo(RedisRateLimiter.REMAINING_HEADER);
		assertThat(properties.getReplenishRateHeader()).isEqualTo(RedisRateLimiter.REPLENISH_RATE_HEADER);
		assertThat(properties.getBurstCapacityHeader()).isEqualTo(RedisRateLimiter.BURST_CAPACITY_HEADER);
		assertThat(properties.getRequestedTokensHeader()).isEqualTo(RedisRateLimiter.REQUESTED_TOKENS_HEADER);
	}

	@Test
	public void rateLimiterExposesInjectedProperties() {
		RedisRateLimiterProperties properties = new RedisRateLimiterProperties();
		RedisRateLimiter rateLimiter = new RedisRateLimiter(1, 1);

		assertThat(rateLimiter.getProperties()).isNotNull();

		rateLimiter.getProperties().setIncludeHeaders(false);
		rateLimiter.getProperties().setRemainingHeader("X-Remaining");

		// deprecated delegates read through to the properties object
		assertThat(rateLimiter.isIncludeHeaders()).isFalse();
		assertThat(rateLimiter.getRemainingHeader()).isEqualTo("X-Remaining");

		// the standalone properties instance is independent
		assertThat(properties.isIncludeHeaders()).isTrue();
	}

	@Test
	public void deprecatedSettersWriteThroughToProperties() {
		RedisRateLimiter rateLimiter = new RedisRateLimiter(1, 1);

		rateLimiter.setIncludeHeaders(false);
		rateLimiter.setRequestedTokensHeader("X-Requested");

		assertThat(rateLimiter.getProperties().isIncludeHeaders()).isFalse();
		assertThat(rateLimiter.getProperties().getRequestedTokensHeader()).isEqualTo("X-Requested");
	}

}
