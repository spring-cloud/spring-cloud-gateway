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
 * @author Gkatziouras Emmanouil
 */
@RunWith(SpringRunner.class)
@SpringBootTest
@DirtiesContext
@ActiveProfiles("local-rate-limiter-config")
public class LocalRateLimiterConfigTests {

	@Autowired
	private LocalRateLimiter rateLimiter;

	@Autowired
	private RouteLocator routeLocator;

	@Before
	public void init() {
		// prime routes since getRoutes() no longer blocks
		routeLocator.getRoutes().collectList().block();
	}

	@Test
	public void localRateConfiguredFromEnvironment() {
		assertFilter("local_rate_limiter_config_test", 10, 4, 1, false);
	}

	@Test
	public void localRateConfiguredFromEnvironmentMinimal() {
		assertFilter("local_rate_limiter_minimal_config_test", 2, 10, 1, false);
	}

	@Test
	public void localRateConfiguredFromJavaAPI() {
		assertFilter("custom_local_rate_limiter", 20, 1, 10, false);
	}

	@Test
	public void localRateConfiguredFromJavaAPIDirectBean() {
		assertFilter("alt_custom_local_rate_limiter", 30, 20, 20, true);
	}

	private void assertFilter(String key, int replenishRate, int refreshPeriod,
			int requestedTokens, boolean useDefaultConfig) {
		LocalRateLimiter.Config config;

		if (useDefaultConfig) {
			config = rateLimiter.getDefaultConfig();
		}
		else {
			assertThat(rateLimiter.getConfig()).containsKey(key);
			config = rateLimiter.getConfig().get(key);
		}
		assertThat(config).isNotNull();
		assertThat(config.getReplenishRate()).isEqualTo(replenishRate);
		assertThat(config.getRefreshPeriod()).isEqualTo(refreshPeriod);
		assertThat(config.getRequestedTokens()).isEqualTo(requestedTokens);

		Route route = routeLocator.getRoutes().filter(r -> r.getId().equals(key)).next()
				.block();
		assertThat(route).isNotNull();
		assertThat(route.getFilters()).hasSize(1);
	}

	@EnableAutoConfiguration(exclude = {
			org.springframework.boot.autoconfigure.data.redis.RedisReactiveAutoConfiguration.class })
	@SpringBootConfiguration
	public static class TestConfig {

		@Bean
		public RouteLocator testRouteLocator(RouteLocatorBuilder builder) {
			return builder.routes().route("custom_local_rate_limiter",
					r -> r.path("/custom").filters(f -> f.requestRateLimiter()
							.rateLimiter(LocalRateLimiter.class,
									rl -> rl.setReplenishRate(20).setRequestedTokens(10))
							.and()).uri("http://localhost"))
					.route("alt_custom_local_rate_limiter",
							r -> r.path("/custom")
									.filters(f -> f.requestRateLimiter(
											c -> c.setRateLimiter(myRateLimiter())))
									.uri("http://localhost"))
					.build();

		}

		@Bean
		public LocalRateLimiter myRateLimiter() {
			LocalRateLimiter localRateLimiter = new LocalRateLimiter(30, 20, 20);
			return localRateLimiter;
		}

	}

}
