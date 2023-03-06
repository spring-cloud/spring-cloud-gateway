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

package org.springframework.cloud.gateway.filter.ratelimit;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Zhuozhi Ji
 */
@SpringBootTest
@DirtiesContext
@Testcontainers
@Tag("DockerRequired")
public class RedisRateLimiterLuaScriptTests {

	static final String KEY_PREFIX = "redis-rate-limiter-lua-script-tests";

	@Container
	public static GenericContainer redis = new GenericContainer<>("redis:5.0.14-alpine").withExposedPorts(6379);

	@Autowired
	ReactiveStringRedisTemplate redisTemplate;

	@Autowired
	RedisScript<List<Long>> redisScript;

	@DynamicPropertySource
	static void containerProperties(DynamicPropertyRegistry registry) {
		registry.add("spring.data.redis.host", redis::getContainerIpAddress);
		registry.add("spring.data.redis.port", redis::getFirstMappedPort);
	}

	static List<String> getKeys(String id) {
		String prefix = KEY_PREFIX + ".{" + id;
		String tokens = prefix + "}.tokens";
		String timestamp = prefix + "}.timestamp";
		return Arrays.asList(tokens, timestamp);
	}

	static List<String> getArgs(long rate, long capacity, long now, long requested) {
		return getArgs(rate, capacity, now, requested, 1);
	}

	static List<String> getArgs(long rate, long capacity, long now, long requested, long refillPeriodSec) {
		return Arrays.asList(rate + "", capacity + "", now + "", requested + "", refillPeriodSec + "");
	}

	@Test
	public void testNewAccess() {
		long rate = 1;
		long capacity = 10;
		long now = System.currentTimeMillis();
		long requested = 1;
		List<String> keys = getKeys("new_access");
		List<String> args = getArgs(rate, capacity, now, requested);
		List<Long> result = redisTemplate.execute(redisScript, keys, args).blockFirst();
		assertThat(result.get(0)).isEqualTo(1);
		assertThat(result.get(1)).isEqualTo(9);

		for (String key : keys) {
			long ttl = redisTemplate.getExpire(key).map(duration -> duration.getSeconds()).block();
			long fillTime = (capacity / rate);
			assertThat(ttl).isGreaterThanOrEqualTo(fillTime);
		}
	}

	@Test
	public void testTokenFilled() {
		long rate = 1;
		long capacity = 10;
		long now = System.currentTimeMillis();
		long requested = 5;
		List<String> keys = getKeys("token_filled");
		List<String> args = getArgs(rate, capacity, now, requested);
		redisTemplate.execute(redisScript, keys, args).blockFirst();

		now = now + 3;
		args = getArgs(rate, capacity, now, requested);
		List<Long> result = redisTemplate.execute(redisScript, keys, args).blockFirst();
		assertThat(result.get(0)).isEqualTo(1);
		assertThat(result.get(1)).isEqualTo(3);

		for (String key : keys) {
			long ttl = redisTemplate.getExpire(key).map(duration -> duration.getSeconds()).block();
			long fillTime = (capacity / rate);
			assertThat(ttl).isGreaterThanOrEqualTo(fillTime);
		}
	}

	@ParameterizedTest
	@CsvSource({
			"A, 1, 10, 3, 3, 2, 5",
			"B, 1, 20, 2, 3, 5, 16",
			"C, 1, 20, 2, 7, 5, 17",
			"D, 2, 20, 2, 7, 5, 18",
			"E, 2, 20, 2, 7, 7, 18",
			"F, 2, 20, 2, 20, 7, 18"
	})
	void testTokenFilledWithNSec(String testId, long rate, long capacity, long requested, long delaySec,
			long refillPeriodSec, long expectedRemainedToken) {

		long now = System.currentTimeMillis();

		List<String> keys = getKeys("token_filled" + testId);
		List<String> args = getArgs(rate, capacity, now, requested, refillPeriodSec);
		redisTemplate.execute(redisScript, keys, args).blockFirst();

		now = now + delaySec;
		args = getArgs(rate, capacity, now, requested, refillPeriodSec);
		List<Long> result = redisTemplate.execute(redisScript, keys, args).blockFirst();

		assert result != null;
		assertThat(result.get(0)).isEqualTo(1);
		assertThat(result.get(1)).isEqualTo(expectedRemainedToken);

		for (String key : keys) {
			long ttl = redisTemplate.getExpire(key).map(Duration::getSeconds).blockOptional()
					.orElse(0L);
			long fillTime = (capacity / rate);
			assertThat(ttl).isGreaterThanOrEqualTo(fillTime);
		}
	}

	@Test
	public void testAfterTillTime() {
		long rate = 1;
		long capacity = 10;
		long now = System.currentTimeMillis();
		long requested = 1;
		List<String> keys = getKeys("after_fill_time");
		List<String> args = getArgs(rate, capacity, now, requested);
		redisTemplate.execute(redisScript, keys, args).blockFirst();

		long fillTime = capacity / rate;
		now = now + fillTime;
		args = getArgs(rate, capacity, now, requested);
		List<Long> result = redisTemplate.execute(redisScript, keys, args).blockFirst();
		assertThat(result.get(0)).isEqualTo(1);
		assertThat(result.get(1)).isEqualTo(9);
	}

	@Test
	public void testTokensNotEnough() {
		long rate = 1;
		long capacity = 10;
		long now = System.currentTimeMillis();
		long requested = 20;
		List<String> keys = getKeys("tokens_not_enough");
		List<String> args = getArgs(rate, capacity, now, requested);
		List<Long> result = redisTemplate.execute(redisScript, keys, args).blockFirst();
		assertThat(result.get(0)).isEqualTo(0);
		assertThat(result.get(1)).isEqualTo(10);
	}

	@ParameterizedTest
	@CsvSource({
			"A, 1, 5, 3, 2, 4",
			"B, 2, 10, 6, 2, 3",
			"C, 3, 20, 11, 3, 4",
			"D, 3, 8, 5, 4, 5"
	})
	void testTokensNotEnoughNSec(String testId, long rate, long capacity, long requested, long delaySec, long refillPeriodSec) {

		long now = System.currentTimeMillis();

		List<String> keys = getKeys("tokens_not_enough" + testId);
		List<String> firstArgs = getArgs(rate, capacity, now, requested, refillPeriodSec);
		List<Long> firstResult = redisTemplate.execute(redisScript, keys, firstArgs).blockFirst();

		assert firstResult != null;
		assertThat(firstResult.get(0)).isEqualTo(1);
		assertThat(firstResult.get(1)).isEqualTo(capacity - requested);

		List<String> secondArgs = getArgs(rate, capacity, now + delaySec, requested, refillPeriodSec);
		List<Long> secondResult = redisTemplate.execute(redisScript, keys, secondArgs).blockFirst();
		assert secondResult != null;
		assertThat(secondResult.get(0)).isZero();
		assertThat(secondResult.get(1)).isEqualTo(capacity - requested);
	}

	@EnableAutoConfiguration
	@SpringBootConfiguration
	public static class TestConfig {

	}

}
