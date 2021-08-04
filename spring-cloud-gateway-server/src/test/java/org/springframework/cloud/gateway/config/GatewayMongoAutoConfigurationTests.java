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

import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.route.MongoRouteDefinitionRepository;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

public class GatewayMongoAutoConfigurationTests {

	@SpringBootTest(classes = Config.class,
			properties = "spring.cloud.gateway.mongo-route-definition-repository.enabled=false")
	public static class MongoRouteDefinitionRepositoryDisabledByProperty {

		@Autowired(required = false)
		private MongoRouteDefinitionRepository mongoRouteDefinitionRepository;

		@Test
		public void mongoRouteDefinitionRepository() {
			assertThat(mongoRouteDefinitionRepository).isNull();
		}

	}

	@SpringBootTest(classes = { GatewayMongoAutoConfiguration.class, Config.class },
			properties = { "spring.cloud.gateway.mongo-route-definition-repository.enabled=true" })
	@Testcontainers
	public static class MongoRouteDefinitionRepositoryEnabledByProperty {

		@Container
		static MongoDBContainer mongoDBContainer = new MongoDBContainer("mongo:4.4.2");

		@Autowired(required = false)
		private MongoRouteDefinitionRepository mongoRouteDefinitionRepository;

		@DynamicPropertySource
		static void setProperties(DynamicPropertyRegistry registry) {
			registry.add("spring.data.mongodb.uri", mongoDBContainer::getReplicaSetUrl);
		}

		@Test
		public void mongoRouteDefinitionRepository() {
			assertThat(mongoRouteDefinitionRepository).isNotNull();
		}

	}

	@SpringBootConfiguration
	@EnableAutoConfiguration
	protected static class Config {

	}

}
