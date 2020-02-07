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

package org.springframework.cloud.gateway.filter.quota;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Spencer Gibb
 * @author Tobias Schug
 */
@RunWith(SpringRunner.class)
@SpringBootTest
@DirtiesContext
@ActiveProfiles("redis-quota-filter-default-config")
public class RedisQuotaFilterDefaultFilterConfigTests {

	@Autowired
	private RedisQuotaFilter quotaFilter;

	@Autowired
	private RouteLocator routeLocator;

	@Before
	public void init() {
		routeLocator.getRoutes().collectList().block(); // prime routes since getRoutes()
		// no longer blocks
	}

	@Test
	public void redisRateConfiguredFromEnvironmentDefaultFilters() {
		String routeId = "redis_quota_filter_config_default_test";
		RedisQuotaFilter.Config config = quotaFilter.loadConfiguration(routeId);
		assertConfigAndRoute(routeId, 10, RedisQuotaFilter.QuotaPeriods.DAYS, config);
	}

	private void assertConfigAndRoute(String key, int limit,
			RedisQuotaFilter.QuotaPeriods period, RedisQuotaFilter.Config config) {
		assertThat(config).isNotNull();
		assertThat(config.getLimit()).isEqualTo(limit);
		assertThat(config.getPeriod()).isEqualTo(period);

		Route route = routeLocator.getRoutes().filter(r -> r.getId().equals(key)).next()
				.block();
		assertThat(route).isNotNull();
		assertThat(route.getFilters()).isNotEmpty();
	}

	@EnableAutoConfiguration
	@SpringBootConfiguration
	public static class TestConfig {

	}

}
