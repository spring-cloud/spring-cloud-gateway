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

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Spencer Gibb
 */
@SpringBootTest
@DirtiesContext
@ActiveProfiles("redis-rate-limiter-config")
public class RedisRateLimiterConfigTests {

	@Autowired
	private RedisRateLimiter rateLimiter;

	@Autowired
	private RouteLocator routeLocator;

	@BeforeEach
	public void init() {
		// prime routes since getRoutes() no longer blocks
		routeLocator.getRoutes().collectList().block();
	}

	@Test
	public void shouldThrowAnErrorWhenReplenishRateIsHigherThanBurstCapacity() {
		Assertions.assertThatThrownBy(() -> new RedisRateLimiter(10, 5)).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	public void redisRateConfiguredFromEnvironment() {
		assertFilter("redis_rate_limiter_config_test", 10, 20, 1, false);
	}

	@Test
	public void redisRateConfiguredFromEnvironmentMinimal() {
		assertFilter("redis_rate_limiter_minimal_config_test", 2, 1, 1, false);
	}

	@Test
	public void redisRateConfiguredFromJavaAPI() {
		assertFilter("custom_redis_rate_limiter", 20, 40, 10, false);
	}

	@Test
	public void redisRateConfiguredFromJavaAPIDirectBean() {
		assertFilter("alt_custom_redis_rate_limiter", 30, 60, 20, true);
	}

	private void assertFilter(String key, int replenishRate, int burstCapacity, int requestedTokens,
			boolean useDefaultConfig) {
		RedisRateLimiter.Config config;

		if (useDefaultConfig) {
			config = rateLimiter.getDefaultConfig();
		}
		else {
			assertThat(rateLimiter.getConfig()).containsKey(key);
			config = rateLimiter.getConfig().get(key);
		}
		assertThat(config).isNotNull();
		assertThat(config.getReplenishRate()).isEqualTo(replenishRate);
		assertThat(config.getBurstCapacity()).isEqualTo(burstCapacity);
		assertThat(config.getRequestedTokens()).isEqualTo(requestedTokens);

		Route route = routeLocator.getRoutes().filter(r -> r.getId().equals(key)).next().block();
		assertThat(route).isNotNull();
		assertThat(route.getFilters()).hasSize(1);
	}

	@EnableAutoConfiguration
	@SpringBootConfiguration
	public static class TestConfig {

		@Bean
		public RouteLocator testRouteLocator(RouteLocatorBuilder builder) {
			return builder.routes()
				.route("custom_redis_rate_limiter",
						r -> r.path("/custom")
							.filters(f -> f.requestRateLimiter()
								.rateLimiter(RedisRateLimiter.class,
										rl -> rl.setBurstCapacity(40).setReplenishRate(20).setRequestedTokens(10))
								.and())
							.uri("http://localhost"))
				.route("alt_custom_redis_rate_limiter",
						r -> r.path("/custom")
							.filters(f -> f.requestRateLimiter(c -> c.setRateLimiter(myRateLimiter())))
							.uri("http://localhost"))
				.build();

		}

		@Bean
		public RedisRateLimiter myRateLimiter() {
			return new RedisRateLimiter(30, 60, 20);
		}

	}

}
