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

package org.springframework.cloud.gateway.filter.redis.quota;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Tobias Schug
 */
@RunWith(SpringRunner.class)
@SpringBootTest
@DirtiesContext
@ActiveProfiles("redis-quota-filter-config")
public class RedisQuotaFilterConfigTests {

	@Autowired
	private RedisQuotaFilter quotaFilter;

	@Autowired
	private RouteLocator routeLocator;

	@Before
	public void init() {
		routeLocator.getRoutes().collectList().block(); // prime routes since getRoutes()
		// no longer blocks
	}

	/* test with values from application-redis-quota-filter-config.yml could be loaded */
	@Test
	public void redisQuotaFilterConfiguredFromEnvironment() {
		assertFilter("redis_quota_filter_config_test", 10, "DAYS", false);
	}

	@Test
	public void redisQuotaFilterConfiguredFromJavaAPI() {
		assertFilter("custom_redis_quota_filter", 1, "MINUTES", false);
	}

	@Test
	public void redisQuotaFilterConfiguredFromJavaAPIDirectBean() {
		assertFilter("alt_custom_redis_redis_quota_filter", 2, "SECONDS", true);
	}

	@Test
	public void redisQuotaFilterAbsPeriod() {
		assertFilter("abs_custom_redis_redis_quota_filter", 1, "ABS", false);
	}

	@Test(expected = IllegalArgumentException.class)
	public void redisQuotaFilterConfigWrongPeriod() {
		quotaFilter.getDefaultConfig().setPeriod("WRONG_TYPE");
	}

	private void assertFilter(String key, int limit, String period,
			boolean useDefaultConfig) {
		RedisQuotaFilter.Config config;

		if (useDefaultConfig) {
			config = quotaFilter.getDefaultConfig();
		}
		else {
			assertThat(quotaFilter.getConfig()).containsKey(key);
			config = quotaFilter.getConfig().get(key);
		}
		assertThat(config).isNotNull();
		assertThat(config.getLimit()).isEqualTo(limit);
		assertThat(config.getPeriod().getTimeUnitName()).isEqualTo(period);

		Route route = routeLocator.getRoutes().filter(r -> r.getId().equals(key)).next()
				.block();
		assertThat(route).isNotNull();
		assertThat(route.getFilters()).hasSize(1);
	}

	@EnableAutoConfiguration
	@SpringBootConfiguration
	public static class TestConfig {

		@Bean
		public RouteLocator testRouteLocator(RouteLocatorBuilder builder) {
			return builder.routes().route("custom_redis_quota_filter", r -> r
					.path("/custom")
					.filters(f -> f.requestQuotaFilter().quotaFilter(
							RedisQuotaFilter.class,
							qf -> qf.setLimit(1).setPeriod(
									RedisQuotaFilter.QuotaPeriods.MINUTES.toString()))
							.and())
					.uri("http://localhost"))
					.route("alt_custom_redis_redis_quota_filter",
							r -> r.path("/custom")
									.filters(f -> f.requestQuotaFilter(
											c -> c.setQuotaFilter(customQuotaFilter())))
									.uri("http://localhost"))
					.route("abs_custom_redis_redis_quota_filter", r -> r.path("/custom")
							.filters(f -> f.requestQuotaFilter().quotaFilter(
									RedisQuotaFilter.class,
									qf -> qf.setLimit(1).setPeriod(
											RedisQuotaFilter.QuotaPeriods.ABS.toString()))
									.and())
							.uri("http://localhost"))
					.build();
		}

		@Bean
		public RedisQuotaFilter customQuotaFilter() {
			return new RedisQuotaFilter(2, "SECONDS");
		}

	}

}
