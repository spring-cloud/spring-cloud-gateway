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

package org.springframework.cloud.gateway.config;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import org.springframework.cloud.gateway.route.RedisRouteDefinitionRepository;
import org.springframework.cloud.gateway.route.RedisRouteDefinitionRepositoryTests;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.test.annotation.DirtiesContext;

import static org.assertj.core.api.Assertions.assertThat;

public class GatewayRedisAutoConfigurationTests {

	@SpringBootConfiguration
	@EnableAutoConfiguration
	protected static class Config {

		// TODO: figure out why I need these
		@Bean
		RedisRouteDefinitionRepositoryTests.TestGatewayFilterFactory testGatewayFilterFactory() {
			return new RedisRouteDefinitionRepositoryTests.TestGatewayFilterFactory();
		}

		@Bean
		RedisRouteDefinitionRepositoryTests.TestFilterGatewayFilterFactory testFilterGatewayFilterFactory() {
			return new RedisRouteDefinitionRepositoryTests.TestFilterGatewayFilterFactory();
		}

		@Bean
		RedisRouteDefinitionRepositoryTests.TestRoutePredicateFactory testRoutePredicateFactory() {
			return new RedisRouteDefinitionRepositoryTests.TestRoutePredicateFactory();
		}

	}

	@Nested
	@SpringBootTest(classes = Config.class)
	class EnabledByDefault {

		@Autowired(required = false)
		private RedisScript redisRequestRateLimiterScript;

		@Autowired(required = false)
		private RedisRateLimiter redisRateLimiter;

		@Test
		public void shouldInjectRedisBeans() {
			assertThat(redisRequestRateLimiterScript).isNotNull();
			assertThat(redisRateLimiter).isNotNull();
		}

	}

	@Nested
	@SpringBootTest(classes = Config.class, properties = "spring.cloud.gateway.redis.enabled=false")
	class DisabledByProperty {

		@Autowired(required = false)
		private RedisScript redisRequestRateLimiterScript;

		@Autowired(required = false)
		private RedisRateLimiter redisRateLimiter;

		@Test
		public void shouldDisableRedisBeans() {
			assertThat(redisRequestRateLimiterScript).isNull();
			assertThat(redisRateLimiter).isNull();
		}

	}

	/**
	 * @author Dennis Menge
	 */
	@Nested
	@SpringBootTest(classes = GatewayRedisAutoConfigurationTests.Config.class,
			properties = "spring.cloud.gateway.redis-route-definition-repository.enabled=false")
	@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
	class RedisRouteDefinitionRepositoryDisabledByProperty {

		@Autowired(required = false)
		private RedisRouteDefinitionRepository redisRouteDefinitionRepository;

		@Test
		public void redisRouteDefinitionRepository() {
			assertThat(redisRouteDefinitionRepository).isNull();
		}

	}

}
