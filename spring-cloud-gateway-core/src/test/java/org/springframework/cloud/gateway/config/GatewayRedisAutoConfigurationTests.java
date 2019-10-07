/*
 * Copyright 2013-2017 the original author or authors.
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
import org.springframework.cloud.gateway.route.RedisRouteDefinitionRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.junit4.SpringRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Dennis Menge
 */
@RunWith(Enclosed.class)
public class GatewayRedisAutoConfigurationTests {

	@RunWith(SpringRunner.class)
	@SpringBootTest(
			classes = RedisRouteDefinitionRepositoryDisabledByProperty.TestConfig.class,
			properties = "spring.cloud.gateway.redis-route-definition-repository.enabled=false")
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

	@RunWith(SpringRunner.class)
	@SpringBootTest(
			classes = RedisRouteDefinitionRepositoryEnabledByProperty.TestConfig.class,
			properties = "spring.cloud.gateway.redis-route-definition-repository.enabled=true")
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
