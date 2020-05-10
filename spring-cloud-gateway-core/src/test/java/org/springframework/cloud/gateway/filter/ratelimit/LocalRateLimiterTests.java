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
import org.springframework.cloud.gateway.filter.ratelimit.RateLimiter.Response;
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
public class LocalRateLimiterTests extends BaseWebClientTests {

	private static final String DEFAULT_ROUTE = "myroute";

	private static final int REPLENISH_RATE = 10;

	private static final int REQUESTED_TOKENS = 1;

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
		addDefaultRoute();
		checkLimitEnforced(id, REPLENISH_RATE, REQUESTED_TOKENS, DEFAULT_ROUTE);
	}

	private void addDefaultRoute() {
		rateLimiter.getConfig().put(DEFAULT_ROUTE, new LocalRateLimiter.Config()
				.setReplenishRate(REPLENISH_RATE).setRequestedTokens(REQUESTED_TOKENS));
	}

	@Test
	public void localRateLimiterWorksForMultipleTokens() throws Exception {
		String id = UUID.randomUUID().toString();

		int replenishRate = 60;
		int refreshPeriod = 60;
		int requestedTokens = 3;

		String routeId = "low_rate_route";
		rateLimiter.getConfig().put(routeId,
				new LocalRateLimiter.Config().setReplenishRate(replenishRate)
						.setRefreshPeriod(refreshPeriod)
						.setRequestedTokens(requestedTokens));

		// # Token consumption works
		simulateRequestsForMultipleTokens(id, replenishRate, requestedTokens, routeId);

		checkLimitReached(id, routeId);

		Thread.sleep(Math.max(1, replenishRate) * 1000);

		// # After the tokens have been replenished , check the steady state
		checkReadyStateForMultipleTokens(id, replenishRate, requestedTokens, routeId);
	}

	private void checkReadyStateForMultipleTokens(String id, int replenishRate,
			int requestedTokens, String routeId) {
		Response response;
		for (int i = 0; i < replenishRate / requestedTokens; i++) {
			response = rateLimiter.isAllowed(routeId, id).block();
			assertThat(response.isAllowed()).as("steady state # %s is allowed", i)
					.isTrue();
		}

		response = rateLimiter.isAllowed(routeId, id).block();
		assertThat(response.isAllowed()).as("steady state # %s is allowed", replenishRate)
				.isFalse();
	}

	private void simulateRequestsForMultipleTokens(String id, int replenishRate,
			int requestedTokens, String routeId) {
		for (int i = 0; i < replenishRate / requestedTokens; i++) {
			Response response = rateLimiter.isAllowed(routeId, id).block();
			assertThat(response.isAllowed()).as("Burst # %s is allowed", i).isTrue();
			assertThat(response.getHeaders())
					.containsKey(LocalRateLimiter.REMAINING_HEADER);
			assertThat(response.getHeaders()).containsEntry(
					LocalRateLimiter.REPLENISH_RATE_HEADER,
					String.valueOf(replenishRate));
			assertThat(response.getHeaders()).containsEntry(
					LocalRateLimiter.REQUESTED_TOKENS_HEADER,
					String.valueOf(requestedTokens));
		}
	}

	@Test
	public void localRateLimiterDoesNotSendHeadersIfDeactivated() throws Exception {
		String id = UUID.randomUUID().toString();
		addDefaultRoute();
		rateLimiter.setIncludeHeaders(false);

		Response response = rateLimiter.isAllowed(DEFAULT_ROUTE, id).block();
		assertThat(response.isAllowed()).isTrue();
		assertThat(response.getHeaders())
				.doesNotContainKey(LocalRateLimiter.REMAINING_HEADER);
		assertThat(response.getHeaders())
				.doesNotContainKey(LocalRateLimiter.REPLENISH_RATE_HEADER);
		assertThat(response.getHeaders())
				.doesNotContainKey(LocalRateLimiter.REQUESTED_TOKENS_HEADER);
	}

	private void checkLimitEnforced(String id, int replenishRate, int requestedTokens,
			String routeId) throws InterruptedException {

		// Token consumption works
		simulateRequests(id, replenishRate, requestedTokens, routeId);

		checkLimitReached(id, routeId);

		Thread.sleep(Math.max(1, requestedTokens / replenishRate) * 1000);

		// # After the tokens have been replenished , check the steady state
		checkSteadyState(id, replenishRate, routeId);
	}

	private void simulateRequests(String id, int replenishRate, int requestedTokens,
			String routeId) {
		for (int i = 0; i < replenishRate / requestedTokens; i++) {
			RateLimiter.Response response = rateLimiter.isAllowed(routeId, id).block();
			assertThat(response.isAllowed()).as("Burst # %s is allowed", i).isTrue();
			assertThat(response.getHeaders())
					.containsKey(LocalRateLimiter.REMAINING_HEADER);
			assertThat(response.getHeaders()).containsEntry(
					LocalRateLimiter.REPLENISH_RATE_HEADER,
					String.valueOf(replenishRate));
			assertThat(response.getHeaders()).containsEntry(
					LocalRateLimiter.REQUESTED_TOKENS_HEADER,
					String.valueOf(requestedTokens));
		}
	}

	private void checkLimitReached(String id, String routeId) {
		RateLimiter.Response response = rateLimiter.isAllowed(routeId, id).block();
		if (response.isAllowed()) { // TODO: sometimes there is an off by one error
			response = rateLimiter.isAllowed(routeId, id).block();
		}
		assertThat(response.isAllowed()).as("Burst # %s is not allowed").isFalse();
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
