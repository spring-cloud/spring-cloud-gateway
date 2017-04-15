package org.springframework.cloud.gateway.filter.factory;

import java.util.List;
import java.util.UUID;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.test.BaseWebClientTests;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.scripting.support.ResourceScriptSource;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.junit4.SpringRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

/**
 * see https://gist.github.com/ptarjan/e38f45f2dfe601419ca3af937fff574d#file-1-check_request_rate_limiter-rb-L36-L62
 * @author Spencer Gibb
 */
@RunWith(SpringRunner.class)
@SpringBootTest(properties = "logging.level.org.springframework.cloud.gateway.filter.factory=DEBUG",
		webEnvironment = RANDOM_PORT)
@DirtiesContext
public class RequestRateLimiterWebFilterFactoryTests extends BaseWebClientTests {

	@Autowired
	private StringRedisTemplate redisTemplate;

	@Autowired
	private RedisScript<List> script;

	@Test
	public void requestRateLimiterWebFilterFactoryWorks() throws Exception {
		String id = UUID.randomUUID().toString();

		RequestRateLimiterWebFilterFactory filterFactory = new RequestRateLimiterWebFilterFactory(this.redisTemplate, this.script);

		int replenishRate = 10;
		int capacity = 5 * replenishRate;

		// Bursts work
		for (int i = 0; i < capacity; i++) {
			boolean allowed = filterFactory.isAllowed(replenishRate, capacity, id);
			assertThat(allowed).isTrue();
		}
		
		boolean allowed = filterFactory.isAllowed(replenishRate, capacity, id);
		assertThat(allowed).isFalse();

		Thread.sleep(1000);

        // # After the burst is done, check the steady state
		for (int i = 0; i < replenishRate; i++) {
			allowed = filterFactory.isAllowed(replenishRate, capacity, id);
			assertThat(allowed).isTrue();
		}

		allowed = filterFactory.isAllowed(replenishRate, capacity, id);
		assertThat(allowed).isFalse();
	}

	@EnableAutoConfiguration
	@SpringBootConfiguration
	@Import(BaseWebClientTests.DefaultTestConfig.class)
	public static class TestConfig {
		@Bean
		public RedisScript<List> requestRateLimiterScript() {
			DefaultRedisScript<List> redisScript = new DefaultRedisScript<>();
			redisScript.setScriptSource(new ResourceScriptSource(new ClassPathResource("META-INF/scripts/request_rate_limiter.lua")));
			redisScript.setResultType(List.class);
			return redisScript;
		}

		@Bean
		public RequestRateLimiterWebFilterFactory requestRateLimiterWebFilterFactory(StringRedisTemplate redisTemplate) {
			return new RequestRateLimiterWebFilterFactory(redisTemplate, requestRateLimiterScript());
		}
	}
}
