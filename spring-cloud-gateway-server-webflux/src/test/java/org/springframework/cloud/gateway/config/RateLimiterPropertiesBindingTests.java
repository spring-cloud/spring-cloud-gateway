/*
 * Copyright 2013-present the original author or authors.
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

package org.springframework.cloud.gateway.config;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.filter.factory.RequestRateLimiterGatewayFilterFactory;
import org.springframework.cloud.gateway.filter.factory.RequestRateLimiterProperties;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiterProperties;
import org.springframework.http.HttpStatus;
import org.springframework.test.annotation.DirtiesContext;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that the extracted {@link RedisRateLimiterProperties} and
 * {@link RequestRateLimiterProperties} are bound from the environment and injected into
 * the beans that consume them.
 *
 * @author Aryamann Singh
 */
@SpringBootTest(properties = { "spring.cloud.gateway.server.webflux.redis-rate-limiter.include-headers=false",
		"spring.cloud.gateway.server.webflux.redis-rate-limiter.remaining-header=X-Remaining-Custom",
		"spring.cloud.gateway.server.webflux.redis-rate-limiter.config.myroute.replenish-rate=99",
		"spring.cloud.gateway.server.webflux.redis-rate-limiter.config.myroute.burst-capacity=199",
		"spring.cloud.gateway.server.webflux.redis-rate-limiter.config.myroute.requested-tokens=7",
		"spring.cloud.gateway.server.webflux.filter.request-rate-limiter.deny-empty-key=false",
		"spring.cloud.gateway.server.webflux.filter.request-rate-limiter.empty-key-status-code=BAD_REQUEST",
		"spring.cloud.gateway.server.webflux.filter.request-rate-limiter.throw-on-limit=true" })
@DirtiesContext
public class RateLimiterPropertiesBindingTests {

	@Autowired
	private RedisRateLimiter redisRateLimiter;

	@Autowired
	private RedisRateLimiterProperties redisRateLimiterProperties;

	@Autowired
	private RequestRateLimiterGatewayFilterFactory requestRateLimiterFactory;

	@Autowired
	private RequestRateLimiterProperties requestRateLimiterProperties;

	@Test
	public void redisRateLimiterPropertiesAreBoundAndInjected() {
		assertThat(redisRateLimiterProperties.isIncludeHeaders()).isFalse();
		assertThat(redisRateLimiterProperties.getRemainingHeader()).isEqualTo("X-Remaining-Custom");
		// the rate limiter bean receives the same bound properties instance
		assertThat(redisRateLimiter.getProperties()).isSameAs(redisRateLimiterProperties);
	}

	@Test
	public void perRouteConfigIsStillBound() {
		// `redis-rate-limiter.config.<routeId>.*` was bindable because RedisRateLimiter
		// was itself the @ConfigurationProperties bean, exposing the inherited
		// AbstractStatefulConfigurable#getConfig() map. The properties bean now owns that
		// map and the rate limiter reads through to it, so the binding is unchanged.
		assertThat(redisRateLimiter.getConfig()).containsKey("myroute");
		RedisRateLimiter.Config routeConfig = redisRateLimiter.getConfig().get("myroute");
		assertThat(routeConfig.getReplenishRate()).isEqualTo(99);
		assertThat(routeConfig.getBurstCapacity()).isEqualTo(199);
		assertThat(routeConfig.getRequestedTokens()).isEqualTo(7);
		assertThat(redisRateLimiter.getConfig()).isSameAs(redisRateLimiterProperties.getConfig());
	}

	@Test
	public void requestRateLimiterPropertiesAreBoundAndInjected() {
		assertThat(requestRateLimiterProperties.isDenyEmptyKey()).isFalse();
		assertThat(requestRateLimiterProperties.getEmptyKeyStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST.name());
		assertThat(requestRateLimiterProperties.isThrowOnLimit()).isTrue();
		assertThat(requestRateLimiterFactory.getProperties()).isSameAs(requestRateLimiterProperties);
	}

	@EnableAutoConfiguration
	@SpringBootConfiguration
	public static class TestConfig {

	}

}
