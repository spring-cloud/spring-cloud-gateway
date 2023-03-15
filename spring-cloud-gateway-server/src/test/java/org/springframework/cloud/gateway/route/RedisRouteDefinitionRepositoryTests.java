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

import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import reactor.core.publisher.Mono;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.filter.FilterDefinition;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.cloud.gateway.handler.predicate.AbstractRoutePredicateFactory;
import org.springframework.cloud.gateway.handler.predicate.PredicateDefinition;
import org.springframework.context.annotation.Bean;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.server.ServerWebExchange;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Dennis Menge
 */
@SpringBootTest(properties = { "debug=true", "logging.level.org.springframework.cloud.gateway=trace" })
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@ActiveProfiles("redis-route-repository")
@Testcontainers
@Tag("DockerRequired")
public class RedisRouteDefinitionRepositoryTests {

	@Container
	public static GenericContainer redis = new GenericContainer<>("redis:5.0.14-alpine").withExposedPorts(6379);

	@Autowired
	private RedisRouteDefinitionRepository redisRouteDefinitionRepository;

	@BeforeAll
	public static void startRedisContainer() {
		redis.start();
	}

	@DynamicPropertySource
	static void containerProperties(DynamicPropertyRegistry registry) {
		registry.add("spring.data.redis.host", redis::getHost);
		registry.add("spring.data.redis.port", redis::getFirstMappedPort);
	}

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
		assertThat(redisRouteDefinitionRepository.getRouteDefinitions().collectList().block().size()).isEqualTo(0);
	}

	private RouteDefinition defaultTestRoute() {
		RouteDefinition testRouteDefinition = new RouteDefinition();
		testRouteDefinition.setId("test-route");
		testRouteDefinition.setUri(URI.create("http://example.org"));

		FilterDefinition prefixPathFilterDefinition = new FilterDefinition("PrefixPath=/test-path");
		FilterDefinition redirectToFilterDefinition = new FilterDefinition("RemoveResponseHeader=Sensitive-Header");
		FilterDefinition testFilterDefinition = new FilterDefinition();
		testFilterDefinition.setName("Test");
		testRouteDefinition.setFilters(
				Arrays.asList(prefixPathFilterDefinition, redirectToFilterDefinition, testFilterDefinition));

		PredicateDefinition hostRoutePredicateDefinition = new PredicateDefinition("Host=myhost.org");
		PredicateDefinition methodRoutePredicateDefinition = new PredicateDefinition("Method=GET");
		PredicateDefinition testPredicateDefinition = new PredicateDefinition("Test=value");
		testRouteDefinition.setPredicates(
				Arrays.asList(hostRoutePredicateDefinition, methodRoutePredicateDefinition, testPredicateDefinition));
		return testRouteDefinition;
	}

	@EnableAutoConfiguration
	@SpringBootConfiguration
	public static class TestConfig {

		@Bean
		TestGatewayFilterFactory testGatewayFilterFactory() {
			return new TestGatewayFilterFactory();
		}

		@Bean
		TestFilterGatewayFilterFactory testFilterGatewayFilterFactory() {
			return new TestFilterGatewayFilterFactory();
		}

		@Bean
		TestRoutePredicateFactory testRoutePredicateFactory() {
			return new TestRoutePredicateFactory();
		}

	}

	public static class TestGatewayFilterFactory extends AbstractGatewayFilterFactory<Object> {

		@Override
		public GatewayFilter apply(Object config) {
			return (exchange, chain) -> chain.filter(exchange);
		}

	}

	public static class TestFilterGatewayFilterFactory extends TestGatewayFilterFactory {

	}

	public static class TestRoutePredicateFactory extends AbstractRoutePredicateFactory<Object> {

		public TestRoutePredicateFactory() {
			super(Object.class);
		}

		@Override
		public Predicate<ServerWebExchange> apply(Object config) {
			return exchange -> true;
		}

	}

}
