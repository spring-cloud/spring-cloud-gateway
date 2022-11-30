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

package org.springframework.cloud.gateway.filter.ratelimit;

import io.lettuce.core.RedisException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;

import org.springframework.cloud.gateway.support.ConfigurationService;
import org.springframework.context.ApplicationContext;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.MapEntry.entry;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

/**
 * @author Denis Cutic
 */
@ExtendWith(MockitoExtension.class)
public class RedisRateLimiterUnitTests {

	private static final int DEFAULT_REPLENISH_RATE = 1;

	private static final int DEFAULT_BURST_CAPACITY = 1;

	public static final String ROUTE_ID = "routeId";

	public static final String REQUEST_ID = "id";

	public static final String[] CONFIGURATION_SERVICE_BEANS = new String[0];

	public static final RedisException REDIS_EXCEPTION = new RedisException("Mocked problem");

	@Mock
	private ApplicationContext applicationContext;

	@Mock
	private ReactiveStringRedisTemplate redisTemplate;

	private RedisRateLimiter redisRateLimiter;

	@BeforeEach
	public void setUp() {
		when(applicationContext.getBean(ReactiveStringRedisTemplate.class)).thenReturn(redisTemplate);
		when(applicationContext.getBeanNamesForType(ConfigurationService.class))
				.thenReturn(CONFIGURATION_SERVICE_BEANS);
		redisRateLimiter = new RedisRateLimiter(DEFAULT_REPLENISH_RATE, DEFAULT_BURST_CAPACITY);
	}

	@AfterEach
	public void tearDown() {
		Mockito.reset(applicationContext);
	}

	@Test
	public void shouldThrowWhenNotInitialized() {
		Assertions.assertThrows(IllegalStateException.class, () -> redisRateLimiter.isAllowed(ROUTE_ID, REQUEST_ID));
	}

	@Test
	public void shouldAllowRequestWhenRedisIssueOccurs() {
		when(redisTemplate.execute(any(), anyList(), anyList())).thenThrow(REDIS_EXCEPTION);
		redisRateLimiter.setApplicationContext(applicationContext);
		Mono<RateLimiter.Response> response = redisRateLimiter.isAllowed(ROUTE_ID, REQUEST_ID);
		assertThat(response.block()).extracting(RateLimiter.Response::isAllowed).isEqualTo(true);
	}

	@Test
	public void shouldReturnHeadersWhenRedisIssueOccurs() {
		when(redisTemplate.execute(any(), anyList(), anyList())).thenThrow(REDIS_EXCEPTION);
		redisRateLimiter.setApplicationContext(applicationContext);
		Mono<RateLimiter.Response> response = redisRateLimiter.isAllowed(ROUTE_ID, REQUEST_ID);
		assertThat(response.block().getHeaders()).containsOnly(entry(redisRateLimiter.getRemainingHeader(), "-1"),
				entry(redisRateLimiter.getBurstCapacityHeader(), DEFAULT_BURST_CAPACITY + ""),
				entry(redisRateLimiter.getReplenishRateHeader(), DEFAULT_REPLENISH_RATE + ""),
				entry(redisRateLimiter.getRequestedTokensHeader(), "1"));
	}

}
