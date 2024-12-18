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

import java.time.Duration;
import java.util.UUID;

import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.caffeine.CaffeineProxyManager;
import io.github.bucket4j.distributed.proxy.AsyncProxyManager;
import io.github.bucket4j.distributed.remote.RemoteBucketState;
import org.junit.jupiter.api.Test;
import org.junitpioneer.jupiter.RetryingTest;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.filter.ratelimit.Bucket4jRateLimiter.RefillStyle;
import org.springframework.cloud.gateway.filter.ratelimit.RateLimiter.Response;
import org.springframework.cloud.gateway.test.BaseWebClientTests;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

/**
 * @author Spencer Gibb
 */
@SpringBootTest(webEnvironment = RANDOM_PORT, properties = "spring.cloud.gateway.redis.enabled=false")
@DirtiesContext
public class Bucket4jRateLimiterTests extends BaseWebClientTests {

	@Autowired
	private Bucket4jRateLimiter rateLimiter;

	@RetryingTest(3)
	public void bucket4jRateLimiterGreedyWorks() throws Exception {
		bucket4jRateLimiterWorks(RefillStyle.GREEDY);
	}

	@RetryingTest(3)
	public void bucket4jRateLimiterIntervallyWorks() throws Exception {
		bucket4jRateLimiterWorks(RefillStyle.INTERVALLY);
	}

	public void bucket4jRateLimiterWorks(RefillStyle refillStyle) throws Exception {
		String id = UUID.randomUUID().toString();

		long capacity = 10;
		int requestedTokens = 1;

		String routeId = "myroute";
		rateLimiter.getConfig()
			.put(routeId,
					new Bucket4jRateLimiter.Config().setRefillStyle(refillStyle)
						.setHeaderName("X-RateLimit-Custom")
						.setCapacity(capacity)
						.setRefillPeriod(Duration.ofSeconds(1)));

		checkLimitEnforced(id, capacity, requestedTokens, routeId);
	}

	@Test
	public void bucket4jRateLimiterIsAllowedFalseWorks() throws Exception {
		String id = UUID.randomUUID().toString();

		int capacity = 1;
		int requestedTokens = 2;

		String routeId = "zero_capacity_route";
		rateLimiter.getConfig()
			.put(routeId,
					new Bucket4jRateLimiter.Config().setCapacity(capacity)
						.setRefillPeriod(Duration.ofSeconds(1))
						.setRequestedTokens(requestedTokens));

		Response response = rateLimiter.isAllowed(routeId, id).block();
		assertThat(response.isAllowed()).isFalse();
	}

	private void checkLimitEnforced(String id, long capacity, int requestedTokens, String routeId)
			throws InterruptedException {
		// Bursts work
		simulateBurst(id, capacity, requestedTokens, routeId);

		checkLimitReached(id, routeId, capacity);

		Thread.sleep(Math.max(1, requestedTokens / capacity) * 1000);

		// # After the burst is done, check the steady state
		checkSteadyState(id, capacity, routeId);
	}

	private void simulateBurst(String id, long capacity, int requestedTokens, String routeId) {
		long previousRemaining = capacity;
		for (int i = 0; i < capacity / requestedTokens; i++) {
			Response response = rateLimiter.isAllowed(routeId, id).block();
			assertThat(response.isAllowed()).as("Burst # %s is allowed", i).isTrue();
			assertThat(response.getHeaders()).containsKey("X-RateLimit-Custom");
			System.err.println("response headers: " + response.getHeaders());
			long remaining = Long.parseLong(response.getHeaders().get("X-RateLimit-Custom"));
			assertThat(remaining).isLessThan(previousRemaining);
			previousRemaining = remaining;
			// TODO: assert additional headers
		}
	}

	private void checkLimitReached(String id, String routeId, long capacity) {
		Response response = rateLimiter.isAllowed(routeId, id).block();
		if (response.isAllowed()) { // TODO: sometimes there is an off by one error
			response = rateLimiter.isAllowed(routeId, id).block();
		}
		assertThat(response.isAllowed()).as("capacity # %s is not allowed", capacity).isFalse();
	}

	private void checkSteadyState(String id, long capacity, String routeId) {
		Response response;
		for (int i = 0; i < capacity; i++) {
			response = rateLimiter.isAllowed(routeId, id).block();
			assertThat(response.isAllowed()).as("steady state # %s is allowed", i).isTrue();
		}

		response = rateLimiter.isAllowed(routeId, id).block();
		assertThat(response.isAllowed()).as("steady state # %s is allowed", capacity).isFalse();
	}

	@EnableAutoConfiguration
	@SpringBootConfiguration
	@Import(DefaultTestConfig.class)
	public static class TestConfig {

		@Bean
		public AsyncProxyManager<String> caffeineProxyManager() {
			Caffeine<String, RemoteBucketState> builder = (Caffeine) Caffeine.newBuilder().maximumSize(100);
			return new CaffeineProxyManager<>(builder, Duration.ofMinutes(1)).asAsync();
		}

	}

}
