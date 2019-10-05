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

package org.springframework.cloud.gateway.route;

import java.io.IOException;
import java.net.URI;
import java.util.Arrays;
import java.util.List;

import javax.annotation.PreDestroy;

import org.jetbrains.annotations.NotNull;
import org.junit.Test;
import org.junit.runner.RunWith;
import reactor.core.publisher.Mono;
import redis.embedded.RedisServer;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.filter.FilterDefinition;
import org.springframework.cloud.gateway.handler.predicate.PredicateDefinition;
import org.springframework.context.annotation.Bean;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Dennis Menge
 */
@RunWith(SpringRunner.class)
@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@ActiveProfiles("redis-route-repository")
public class RedisRouteDefinitionRepositoryTests {
	@Autowired
	private RedisRouteDefinitionRepository redisRouteDefinitionRepository;

	@Test
	public void testAddRouteToRedis() {

		RouteDefinition testRouteDefinition = defaultTestRoute();

		redisRouteDefinitionRepository.save(Mono.just(testRouteDefinition)).block();

		List<RouteDefinition> routeDefinitions = redisRouteDefinitionRepository.getRouteDefinitions().collectList()
				.block();

		assertThat(routeDefinitions.size()).isEqualTo(1);
		assertThat(routeDefinitions.get(0)).isEqualTo(testRouteDefinition);
	}

	@Test
	public void testRemoveRouteFromRedis() {

		RouteDefinition testRouteDefinition = defaultTestRoute();

		redisRouteDefinitionRepository.save(Mono.just(testRouteDefinition)).block();

		List<RouteDefinition> routeDefinitions = redisRouteDefinitionRepository.getRouteDefinitions().collectList()
				.block();
		String routeId = routeDefinitions.get(0).getId();

		// Assert that route has been added.
		assertThat(routeDefinitions.size()).isEqualTo(1);

		// Delete route from repository
		redisRouteDefinitionRepository.delete(Mono.just(routeId)).block();

		// Assert that route has been removed.
		assertThat(redisRouteDefinitionRepository.getRouteDefinitions().collectList()
				.block().size()).isEqualTo(0);
	}


	@NotNull
	private RouteDefinition defaultTestRoute() {
		RouteDefinition testRouteDefinition = new RouteDefinition();
		testRouteDefinition.setUri(URI.create("http://example.org"));

		FilterDefinition prefixPathFilterDefinition = new FilterDefinition(
				"PrefixPath=/test-path");
		FilterDefinition redirectToFilterDefinition = new FilterDefinition(
				"RemoveResponseHeader=Sensitive-Header");
		FilterDefinition testFilterDefinition = new FilterDefinition("TestFilter");
		testRouteDefinition.setFilters(Arrays.asList(prefixPathFilterDefinition,
				redirectToFilterDefinition, testFilterDefinition));

		PredicateDefinition hostRoutePredicateDefinition = new PredicateDefinition(
				"Host=myhost.org");
		PredicateDefinition methodRoutePredicateDefinition = new PredicateDefinition(
				"Method=GET");
		PredicateDefinition testPredicateDefinition = new PredicateDefinition(
				"Test=value");
		testRouteDefinition.setPredicates(Arrays.asList(hostRoutePredicateDefinition,
				methodRoutePredicateDefinition, testPredicateDefinition));
		return testRouteDefinition;
	}

	@EnableAutoConfiguration
	@SpringBootConfiguration
	public static class TestConfig {
		RedisServer redisServer;

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