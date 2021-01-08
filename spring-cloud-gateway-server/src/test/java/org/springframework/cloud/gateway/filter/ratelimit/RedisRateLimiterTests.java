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

import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junitpioneer.jupiter.RetryingTest;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.filter.ratelimit.RateLimiter.Response;
import org.springframework.cloud.gateway.test.BaseWebClientTests;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assume.assumeThat;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

/**
 * see
 * https://gist.github.com/ptarjan/e38f45f2dfe601419ca3af937fff574d#file-1-check_request_rate_limiter-rb-L36-L62
 *
 * @author Spencer Gibb
 * @author Ronny Bräunlich
 * @author Denis Cutic
 */
@SpringBootTest(webEnvironment = RANDOM_PORT)
@DirtiesContext
@Testcontainers
public class RedisRateLimiterTests extends BaseWebClientTests {

	@Container
	public GenericContainer redis = new GenericContainer<>("redis:5.0.9-alpine").withExposedPorts(6379);

	@Autowired
	private RedisRateLimiter rateLimiter;

	@BeforeEach
	public void setUp() {
		assumeThat("Ignore on Circle", System.getenv("CIRCLECI"), is(nullValue()));
	}

	@AfterEach
	public void tearDown() {
		rateLimiter.setIncludeHeaders(true);
	}

	@RetryingTest(3)
	public void redisRateLimiterWorks() throws Exception {
		String id = UUID.randomUUID().toString();

		int replenishRate = 10;
		int burstCapacity = 2 * replenishRate;
		int requestedTokens = 1;

		String routeId = "myroute";
		rateLimiter.getConfig().put(routeId, new RedisRateLimiter.Config().setBurstCapacity(burstCapacity)
				.setReplenishRate(replenishRate).setRequestedTokens(requestedTokens));

		checkLimitEnforced(id, replenishRate, burstCapacity, requestedTokens, routeId);
	}

	@Test
	public void redisRateLimiterWorksForLowRates() throws Exception {
		String id = UUID.randomUUID().toString();

		int replenishRate = 1;
		int burstCapacity = 3;
		int requestedTokens = 3;

		String routeId = "low_rate_route";
		rateLimiter.getConfig().put(routeId, new RedisRateLimiter.Config().setBurstCapacity(burstCapacity)
				.setReplenishRate(replenishRate).setRequestedTokens(requestedTokens));

		checkLimitEnforced(id, replenishRate, burstCapacity, requestedTokens, routeId);
	}

	@Test
	public void redisRateLimiterWorksForZeroBurstCapacity() throws Exception {
		String id = UUID.randomUUID().toString();

		int replenishRate = 1;
		int burstCapacity = 0;
		int requestedTokens = 1;

		String routeId = "zero_burst_capacity_route";
		rateLimiter.getConfig().put(routeId, new RedisRateLimiter.Config().setBurstCapacity(burstCapacity)
				.setReplenishRate(replenishRate).setRequestedTokens(requestedTokens));

		Response response = rateLimiter.isAllowed(routeId, id).block();
		assertThat(response.isAllowed()).isFalse();
	}

	@Test
	public void keysUseRedisKeyHashTags() {
		assertThat(RedisRateLimiter.getKeys("1")).containsExactly("request_rate_limiter.{1}.tokens",
				"request_rate_limiter.{1}.timestamp");
	}

	@Test
	public void redisRateLimiterDoesNotSendHeadersIfDeactivated() throws Exception {
		String id = UUID.randomUUID().toString();
		String routeId = "myroute";

		rateLimiter.setIncludeHeaders(false);

		Response response = rateLimiter.isAllowed(routeId, id).block();
		assertThat(response.isAllowed()).isTrue();
		assertThat(response.getHeaders()).doesNotContainKey(RedisRateLimiter.REMAINING_HEADER);
		assertThat(response.getHeaders()).doesNotContainKey(RedisRateLimiter.REPLENISH_RATE_HEADER);
		assertThat(response.getHeaders()).doesNotContainKey(RedisRateLimiter.BURST_CAPACITY_HEADER);
		assertThat(response.getHeaders()).doesNotContainKey(RedisRateLimiter.REQUESTED_TOKENS_HEADER);
	}

	private void checkLimitEnforced(String id, int replenishRate, int burstCapacity, int requestedTokens,
			String routeId) throws InterruptedException {
		// Bursts work
		simulateBurst(id, replenishRate, burstCapacity, requestedTokens, routeId);

		checkLimitReached(id, burstCapacity, routeId);

		Thread.sleep(Math.max(1, requestedTokens / replenishRate) * 1000);

		// # After the burst is done, check the steady state
		checkSteadyState(id, replenishRate, routeId);
	}

	private void simulateBurst(String id, int replenishRate, int burstCapacity, int requestedTokens, String routeId) {
		for (int i = 0; i < burstCapacity / requestedTokens; i++) {
			Response response = rateLimiter.isAllowed(routeId, id).block();
			assertThat(response.isAllowed()).as("Burst # %s is allowed", i).isTrue();
			assertThat(response.getHeaders()).containsKey(RedisRateLimiter.REMAINING_HEADER);
			assertThat(response.getHeaders()).containsEntry(RedisRateLimiter.REPLENISH_RATE_HEADER,
					String.valueOf(replenishRate));
			assertThat(response.getHeaders()).containsEntry(RedisRateLimiter.BURST_CAPACITY_HEADER,
					String.valueOf(burstCapacity));
			assertThat(response.getHeaders()).containsEntry(RedisRateLimiter.REQUESTED_TOKENS_HEADER,
					String.valueOf(requestedTokens));
		}
	}

	private void checkLimitReached(String id, int burstCapacity, String routeId) {
		Response response = rateLimiter.isAllowed(routeId, id).block();
		if (response.isAllowed()) { // TODO: sometimes there is an off by one error
			response = rateLimiter.isAllowed(routeId, id).block();
		}
		assertThat(response.isAllowed()).as("Burst # %s is not allowed", burstCapacity).isFalse();
	}

	private void checkSteadyState(String id, int replenishRate, String routeId) {
		Response response;
		for (int i = 0; i < replenishRate; i++) {
			response = rateLimiter.isAllowed(routeId, id).block();
			assertThat(response.isAllowed()).as("steady state # %s is allowed", i).isTrue();
		}

		response = rateLimiter.isAllowed(routeId, id).block();
		assertThat(response.isAllowed()).as("steady state # %s is allowed", replenishRate).isFalse();
	}

	@EnableAutoConfiguration
	@SpringBootConfiguration
	@Import(BaseWebClientTests.DefaultTestConfig.class)
	public static class TestConfig {

	}

}
