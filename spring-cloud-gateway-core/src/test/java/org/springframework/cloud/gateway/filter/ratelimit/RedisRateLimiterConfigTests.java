/*
 * Copyright 2013-2018 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package org.springframework.cloud.gateway.filter.ratelimit;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter.Config;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.web.server.ServerWebExchange;

import static org.assertj.core.api.Assertions.assertThat;

import reactor.core.publisher.Mono;

/**
 * @author Spencer Gibb
 */
@RunWith(SpringRunner.class)
@SpringBootTest
@DirtiesContext
@ActiveProfiles("redis-rate-limiter-config")
public class RedisRateLimiterConfigTests {

	@Autowired
	private RedisRateLimiter rateLimiter;

	@Autowired
	private RouteLocator routeLocator;

	@Test
	public void redisRateConfiguredFromEnvironment() {
		assertFilter("redis_rate_limiter_config_test", 10, 20, PrincipalNameKeyResolver.class);
	}

	@Test
	public void redisRateConfiguredFromJavaAPI() {
		assertFilter("custom_redis_rate_limiter", 20, 40, MyKeyResolver.class);
	}

	private void assertFilter(String key, int replenishRate, int burstCapacity, Class<? extends KeyResolver> keyResolverClass) {
		assertThat(rateLimiter.getConfig()).containsKey(key);

		Config config = rateLimiter.getConfig().get(key);
		assertThat(config.getReplenishRate()).isEqualTo(replenishRate);
		assertThat(config.getBurstCapacity()).isEqualTo(burstCapacity);

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
					.route("custom_redis_rate_limiter", r -> r.path("/custom")
							.filters(f -> f.requestRateLimiter()
									.rateLimiter(RedisRateLimiter.class,
											rl -> rl.setBurstCapacity(40).setReplenishRate(20))
									.and())
							.uri("http://localhost"))
					.build();

		}
	}

	private static class MyKeyResolver implements KeyResolver {
		@Override
		public Mono<String> resolve(ServerWebExchange exchange) {
			return null;
		}
	}
}
