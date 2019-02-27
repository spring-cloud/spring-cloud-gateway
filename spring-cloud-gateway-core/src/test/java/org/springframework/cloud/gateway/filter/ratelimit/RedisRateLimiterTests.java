package org.springframework.cloud.gateway.filter.ratelimit;

import java.util.UUID;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.filter.ratelimit.RateLimiter.Response;
import org.springframework.cloud.gateway.test.BaseWebClientTests;
import org.springframework.cloud.gateway.test.support.redis.RedisRule;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.junit4.SpringRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assume.assumeThat;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

/**
 * see https://gist.github.com/ptarjan/e38f45f2dfe601419ca3af937fff574d#file-1-check_request_rate_limiter-rb-L36-L62
 * @author Spencer Gibb
 * @author Ronny Bräunlich
 */
@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = RANDOM_PORT)
@DirtiesContext
public class RedisRateLimiterTests extends BaseWebClientTests {

	@Rule
	public final RedisRule redis = RedisRule.bindToDefaultPort();

	@Autowired
	private RedisRateLimiter rateLimiter;

	@Test
	public void redisRateLimiterWorks() throws Exception {
		assumeThat("Ignore on Circle",
				System.getenv("CIRCLECI"), is(nullValue()));

		String id = UUID.randomUUID().toString();

		int replenishRate = 10;
		int burstCapacity = 2 * replenishRate;

		String routeId = "myroute";
		rateLimiter.getConfig().put(routeId, new RedisRateLimiter.Config()
				.setBurstCapacity(burstCapacity)
				.setReplenishRate(replenishRate));

		// Bursts work
		for (int i = 0; i < burstCapacity; i++) {
			Response response = rateLimiter.isAllowed(routeId, id).block();
			assertThat(response.isAllowed()).as("Burst # %s is allowed", i).isTrue();
			assertThat(response.getHeaders()).containsKey(RedisRateLimiter.REMAINING_HEADER);
			assertThat(response.getHeaders()).
					containsEntry(RedisRateLimiter.REPLENISH_RATE_HEADER, String.valueOf(replenishRate));
			assertThat(response.getHeaders()).
					containsEntry(RedisRateLimiter.BURST_CAPACITY_HEADER, String.valueOf(burstCapacity));
		}

		Response response = rateLimiter.isAllowed(routeId, id).block();
		if (response.isAllowed()) { //TODO: sometimes there is an off by one error
			response = rateLimiter.isAllowed(routeId, id).block();
		}
		assertThat(response.isAllowed()).as("Burst # %s is not allowed", burstCapacity).isFalse();

		Thread.sleep(1000);

		// # After the burst is done, check the steady state
		for (int i = 0; i < replenishRate; i++) {
			response = rateLimiter.isAllowed(routeId, id).block();
			assertThat(response.isAllowed()).as("steady state # %s is allowed", i).isTrue();
		}

		response = rateLimiter.isAllowed(routeId, id).block();
		assertThat(response.isAllowed()).as("steady state # %s is allowed", replenishRate).isFalse();
	}

	@Test
	public void keysUseRedisKeyHashTags() {
		assertThat(RedisRateLimiter.getKeys("1"))
				.containsExactly("request_rate_limiter.{1}.tokens", "request_rate_limiter.{1}.timestamp");
	}

	@Test
	public void redisRateLimiterDoesNotSendHeadersIfDeactivated() throws Exception {
		assumeThat("Ignore on Circle",
				System.getenv("CIRCLECI"), is(nullValue()));

		String id = UUID.randomUUID().toString();
		String routeId = "myroute";

		rateLimiter.setIncludeHeaders(false);

		Response response = rateLimiter.isAllowed(routeId, id).block();
		assertThat(response.isAllowed()).isTrue();
		assertThat(response.getHeaders())
				.doesNotContainKey(RedisRateLimiter.REMAINING_HEADER);
		assertThat(response.getHeaders()).
				doesNotContainKey(RedisRateLimiter.REPLENISH_RATE_HEADER);
		assertThat(response.getHeaders()).
				doesNotContainKey(RedisRateLimiter.BURST_CAPACITY_HEADER);
	}

	@EnableAutoConfiguration
	@SpringBootConfiguration
	@Import(BaseWebClientTests.DefaultTestConfig.class)
	public static class TestConfig {

	}
}
