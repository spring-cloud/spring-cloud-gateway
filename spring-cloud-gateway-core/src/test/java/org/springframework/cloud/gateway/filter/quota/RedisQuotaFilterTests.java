/*
 * Copyright 2017-2019 the original author or authors.
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

package org.springframework.cloud.gateway.filter.quota;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
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
import static org.springframework.cloud.gateway.filter.quota.QuotaFilter.Response;

/**
 * @author Tobias Schug
 */
@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = RANDOM_PORT)
@DirtiesContext
public class RedisQuotaFilterTests extends BaseWebClientTests {

	@Rule
	public final RedisRule redis = RedisRule.bindToDefaultPort();

	@Autowired
	private RedisQuotaFilter quotaFilter;

	@Test
	public void redisQuotaFilterWorks() throws Exception {
		assumeThat("Ignore on Circle", System.getenv("CIRCLECI"), is(nullValue()));

		String id = UUID.randomUUID().toString();

		String routeId = "myroute";
		String period = "days";
		int limit = 2;

		quotaFilter.getConfig().put(routeId,
				new RedisQuotaFilter.Config().setLimit(limit).setPeriod(period));
		for (int i = 0; i < limit; i++) {
			Response response = quotaFilter.isAllowed(routeId, id).block();
			assertThat(response.isAllowed()).as("Quota filter not exceeded - %s", i)
					.isTrue();

			assertThat(response.getHeaders())
					.containsKey(RedisQuotaFilter.REMAINING_HEADER);

			assertThat(response.getHeaders().get(RedisQuotaFilter.REMAINING_HEADER))
					.isEqualTo(String.valueOf(limit - (i + 1)));

			assertThat(response.getHeaders())
					.containsKey(RedisQuotaFilter.QUOTA_PERIOD_HEADER);
			assertThat(response.getHeaders().get(RedisQuotaFilter.QUOTA_PERIOD_HEADER))
					.isEqualTo(RedisQuotaFilter.QuotaPeriods.DAYS.getTimeUnitName());

			assertThat(response.getHeaders())
					.containsKey(RedisQuotaFilter.QUOTA_LIMIT_HEADER);
			assertThat(response.getHeaders().get(RedisQuotaFilter.QUOTA_LIMIT_HEADER))
					.isEqualTo(String.valueOf(limit));
		}

		Response response = quotaFilter.isAllowed(routeId, id).block();
		assertThat(response.isAllowed()).as("Quota filter has exceeded").isFalse();
		assertThat(response.getHeaders().get(RedisQuotaFilter.REMAINING_HEADER)).isEqualTo(String.valueOf(0));
	}

	@Test
	public void redisQuotaFilterLockQuotaAfterTimedOut() throws Exception {
		assumeThat("Ignore on Circle", System.getenv("CIRCLECI"), is(nullValue()));

		String id = UUID.randomUUID().toString();

		String routeId = "myroute";
		String period = "seconds";
		int limit = 1;

		quotaFilter.getConfig().put(routeId,
				new RedisQuotaFilter.Config().setLimit(limit).setPeriod(period));

		Response response = quotaFilter.isAllowed(routeId, id).block();
		assertThat(response.isAllowed()).as("Quota filter not exceeded").isTrue();

		assertThat(response.getHeaders().get(RedisQuotaFilter.REMAINING_HEADER))
				.isEqualTo(String.valueOf(0));
		assertThat(response.getHeaders().get(RedisQuotaFilter.QUOTA_PERIOD_HEADER))
				.isEqualTo(RedisQuotaFilter.QuotaPeriods.SECONDS.getTimeUnitName());
		assertThat(response.getHeaders().get(RedisQuotaFilter.QUOTA_LIMIT_HEADER))
				.isEqualTo(String.valueOf(limit));

		// check that the filter block the request
		response = quotaFilter.isAllowed(routeId, id).block();
		assertThat(response.isAllowed()).as("Quota filter has exceeded").isFalse();
		// wait 2sec till the quota was dropped and started from 0 again
		Thread.sleep(TimeUnit.SECONDS.toMillis(2L));
		response = quotaFilter.isAllowed(routeId, id).block();
		assertThat(response.isAllowed()).as("Quota filter has exceeded").isTrue();
		assertThat(response.getHeaders().get(RedisQuotaFilter.REMAINING_HEADER))
				.isEqualTo(String.valueOf(0));
	}

	@Test
	public void keysUseRedisKeyHashTags() {
		assertThat(RedisQuotaFilter.getKey("1"))
				.containsExactly("request_quota_limiter.{1}.tokens");
	}

	@Test
	public void redisRateQuotaFilterDoesNotSendHeadersIfDeactivated() throws Exception {
		assumeThat("Ignore on Circle", System.getenv("CIRCLECI"), is(nullValue()));

		String id = UUID.randomUUID().toString();
		String routeId = "myroute";

		quotaFilter.setIncludeHeaders(false);

		Response response = quotaFilter.isAllowed(routeId, id).block();
		assertThat(response.isAllowed()).isTrue();
		assertThat(response.getHeaders())
				.doesNotContainKey(RedisQuotaFilter.QUOTA_PERIOD_HEADER);
		assertThat(response.getHeaders())
				.doesNotContainKey(RedisQuotaFilter.QUOTA_LIMIT_HEADER);
		assertThat(response.getHeaders())
				.doesNotContainKey(RedisQuotaFilter.REMAINING_HEADER);
	}

	@EnableAutoConfiguration
	@SpringBootConfiguration
	@Import(DefaultTestConfig.class)
	public static class TestConfig {

	}

}
