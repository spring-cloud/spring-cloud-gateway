/*
 * Copyright 2013-2019 the original author or authors.
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

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.test.BaseWebClientTests;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.junit4.SpringRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assume.assumeThat;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

/**
 * @author Gkatziouras Emmanouil
 */
@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = RANDOM_PORT)
@DirtiesContext
public class LocalRateLimiterTests {

	@Autowired
	private LocalRateLimiter rateLimiter;

	@Before
	public void setUp() throws Exception {
		assumeThat("Ignore on Circle", System.getenv("CIRCLECI"), is(nullValue()));
	}

	@After
	public void tearDown() throws Exception {
		rateLimiter.setIncludeHeaders(true);
	}

	@Test
	public void localRateLimiterWorks() throws Exception {
		String id = UUID.randomUUID().toString();

		int replenishRate = 10;
		int burstCapacity = 2 * replenishRate;
		int requestedTokens = 1;

		String routeId = "myroute";

		rateLimiter.getConfig().put(routeId,
				new LocalRateLimiter.Config().setBurstCapacity(burstCapacity)
						.setReplenishRate(replenishRate)
						.setRequestedTokens(requestedTokens));

		checkLimitEnforced(id, replenishRate, burstCapacity, requestedTokens, routeId);
	}

	private void checkLimitEnforced(String id, int replenishRate, int burstCapacity,
			int requestedTokens, String routeId) throws InterruptedException {
		// Bursts work
		simulateBurst(id, replenishRate, burstCapacity, requestedTokens, routeId);

		checkLimitReached(id, burstCapacity, routeId);

		Thread.sleep(Math.max(1, requestedTokens / replenishRate) * 1000);

		// # After the burst is done, check the steady state
		checkSteadyState(id, replenishRate, routeId);
	}

	private void simulateBurst(String id, int replenishRate, int burstCapacity,
			int requestedTokens, String routeId) {
		for (int i = 0; i < burstCapacity / requestedTokens; i++) {
			RateLimiter.Response response = rateLimiter.isAllowed(routeId, id).block();
			assertThat(response.isAllowed()).as("Burst # %s is allowed", i).isTrue();
			assertThat(response.getHeaders())
					.containsKey(LocalRateLimiter.REMAINING_HEADER);
			assertThat(response.getHeaders()).containsEntry(
					LocalRateLimiter.REPLENISH_RATE_HEADER,
					String.valueOf(replenishRate));
			assertThat(response.getHeaders()).containsEntry(
					LocalRateLimiter.BURST_CAPACITY_HEADER,
					String.valueOf(burstCapacity));
			assertThat(response.getHeaders()).containsEntry(
					LocalRateLimiter.REQUESTED_TOKENS_HEADER,
					String.valueOf(requestedTokens));
		}
	}

	private void checkLimitReached(String id, int burstCapacity, String routeId) {
		RateLimiter.Response response = rateLimiter.isAllowed(routeId, id).block();
		if (response.isAllowed()) { // TODO: sometimes there is an off by one error
			response = rateLimiter.isAllowed(routeId, id).block();
		}
		assertThat(response.isAllowed()).as("Burst # %s is not allowed", burstCapacity)
				.isFalse();
	}

	private void checkSteadyState(String id, int replenishRate, String routeId) {
		RateLimiter.Response response;
		for (int i = 0; i < replenishRate; i++) {
			response = rateLimiter.isAllowed(routeId, id).block();
			assertThat(response.isAllowed()).as("steady state # %s is allowed", i)
					.isTrue();
		}

		response = rateLimiter.isAllowed(routeId, id).block();
		assertThat(response.isAllowed()).as("steady state # %s is allowed", replenishRate)
				.isFalse();
	}

	@EnableAutoConfiguration(exclude = {
			org.springframework.boot.autoconfigure.data.redis.RedisReactiveAutoConfiguration.class })
	@SpringBootConfiguration
	@Import(BaseWebClientTests.DefaultTestConfig.class)
	public static class TestConfig {

	}

}
