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

import java.io.IOException;

import javax.annotation.PreDestroy;

import org.junit.Test;
import org.junit.experimental.runners.Enclosed;
import org.junit.runner.RunWith;
import redis.embedded.RedisServer;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import org.springframework.cloud.gateway.route.RedisRouteDefinitionRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.test.context.junit4.SpringRunner;

import static org.assertj.core.api.Assertions.assertThat;

@RunWith(Enclosed.class)
public class GatewayRedisAutoConfigurationTests {

	@SpringBootConfiguration
	@EnableAutoConfiguration
	protected static class Config {

	}

	@RunWith(SpringRunner.class)
	@SpringBootTest(classes = Config.class)
	public static class EnabledByDefault {

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

	@RunWith(SpringRunner.class)
	@SpringBootTest(classes = Config.class, properties = "spring.cloud.gateway.redis.enabled=false")
	public static class DisabledByProperty {

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
	@RunWith(SpringRunner.class)
	@SpringBootTest(
			classes = RedisRouteDefinitionRepositoryDisabledByProperty.TestConfig.class,
			properties = "spring.cloud.gateway.redis-route-definition-repository.enabled=false")
	@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
	public static class RedisRouteDefinitionRepositoryDisabledByProperty {

		@Autowired(required = false)
		private RedisRouteDefinitionRepository redisRouteDefinitionRepository;

		@Test
		public void redisRouteDefinitionRepository() {
			assertThat(redisRouteDefinitionRepository).isNull();
		}

		@EnableAutoConfiguration
		@SpringBootConfiguration
		public static class TestConfig {

		}

	}

	/**
	 * @author Dennis Menge
	 */
	@RunWith(SpringRunner.class)
	@SpringBootTest(
			classes = RedisRouteDefinitionRepositoryEnabledByProperty.TestConfig.class,
			properties = "spring.cloud.gateway.redis-route-definition-repository.enabled=true")
	@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
	public static class RedisRouteDefinitionRepositoryEnabledByProperty {

		@Autowired(required = false)
		private RedisRouteDefinitionRepository redisRouteDefinitionRepository;

		@Test
		public void redisRouteDefinitionRepository() {
			assertThat(redisRouteDefinitionRepository).isNotNull();
		}

		@EnableAutoConfiguration
		@SpringBootConfiguration
		public static class TestConfig {

			private RedisServer redisServer;

			@Bean
			public RedisServer redisServer() throws IOException {
				redisServer = new RedisServer();
				redisServer.start();
				return redisServer;
			}

			@PreDestroy
			public void destroy() {
				redisServer.stop();
			}

		}

	}
}
