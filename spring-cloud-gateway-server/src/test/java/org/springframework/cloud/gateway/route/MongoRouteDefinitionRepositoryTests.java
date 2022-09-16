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

import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.filter.FilterDefinition;
import org.springframework.cloud.gateway.handler.predicate.PredicateDefinition;
import org.springframework.cloud.gateway.support.NotFoundException;
import org.springframework.data.mongodb.repository.config.EnableReactiveMongoRepositories;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@ActiveProfiles("mongo-route-repository")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
		classes = MongoRouteDefinitionRepositoryTests.TestConfig.class)
public class MongoRouteDefinitionRepositoryTests {

	@Container
	static MongoDBContainer mongoDBContainer = new MongoDBContainer("mongo:4.4.2");

	@Autowired
	private MongoRouteDefinitionRepository mongoRouteDefinitionRepository;

	@DynamicPropertySource
	static void setProperties(DynamicPropertyRegistry registry) {
		registry.add("spring.data.mongodb.uri", mongoDBContainer::getReplicaSetUrl);
	}

	@Test
	public void testAddRouteToMongo() {

		RouteDefinition testRouteDefinition = defaultTestRoute();

		mongoRouteDefinitionRepository.save(Mono.just(testRouteDefinition)).block();

		List<RouteDefinition> routeDefinitions = mongoRouteDefinitionRepository.getRouteDefinitions().collectList()
				.block();

		assertThat(routeDefinitions.size()).isEqualTo(1);
		assertThat(routeDefinitions.get(0)).isEqualTo(testRouteDefinition);
	}

	@Test
	public void testAddDifferentRouteToMongo() {

		RouteDefinition testRouteDefinition = defaultTestRoute();
		RouteDefinition testRouteDefinitionTwo = defaultTestRoute();
		testRouteDefinitionTwo.setId("test-route2");

		mongoRouteDefinitionRepository.save(Mono.just(testRouteDefinition)).block();
		mongoRouteDefinitionRepository.save(Mono.just(testRouteDefinitionTwo)).block();

		List<RouteDefinition> routeDefinitions = mongoRouteDefinitionRepository.getRouteDefinitions().collectList()
				.block();

		assertThat(routeDefinitions.size()).isEqualTo(2);
		assertThat(routeDefinitions.get(0)).isEqualTo(testRouteDefinition);
		assertThat(routeDefinitions.get(1)).isEqualTo(testRouteDefinitionTwo);
	}

	@Test
	public void testAddDuplicateRouteToMongo() {

		RouteDefinition testRouteDefinition = defaultTestRoute();
		RouteDefinition testRouteDefinitionDuplicate = defaultTestRoute();

		mongoRouteDefinitionRepository.save(Mono.just(testRouteDefinition)).block();
		mongoRouteDefinitionRepository.save(Mono.just(testRouteDefinitionDuplicate)).block();

		List<RouteDefinition> routeDefinitions = mongoRouteDefinitionRepository.getRouteDefinitions().collectList()
				.block();

		assertThat(routeDefinitions.size()).isEqualTo(1);
		assertThat(routeDefinitions.get(0)).isEqualTo(testRouteDefinition);
	}

	@Test
	public void testRemoveRouteFromMongo() {

		RouteDefinition testRouteDefinition = defaultTestRoute();

		mongoRouteDefinitionRepository.save(Mono.just(testRouteDefinition)).block();

		List<RouteDefinition> routeDefinitions = mongoRouteDefinitionRepository.getRouteDefinitions().collectList()
				.block();
		String routeId = routeDefinitions.get(0).getId();

		// Assert that route has been added.
		assertThat(routeDefinitions.size()).isEqualTo(1);

		// Delete route from repository
		mongoRouteDefinitionRepository.delete(Mono.just(routeId)).block();

		// Assert that route has been removed.
		assertThat(mongoRouteDefinitionRepository.getRouteDefinitions().collectList().block().size()).isEqualTo(0);
	}

	@Test
	public void testDeleteNonExistingRouteFromMongo() {

		// Delete route from repository
		StepVerifier.create(mongoRouteDefinitionRepository.delete(Mono.just("non-existing")))
				.expectError(NotFoundException.class).verify();
	}

	@NotNull
	private RouteDefinition defaultTestRoute() {
		RouteDefinition testRouteDefinition = new RouteDefinition();
		testRouteDefinition.setId("test-route");
		testRouteDefinition.setUri(URI.create("http://example.org"));

		FilterDefinition prefixPathFilterDefinition = new FilterDefinition("PrefixPath=/test-path");
		FilterDefinition redirectToFilterDefinition = new FilterDefinition("RemoveResponseHeader=Sensitive-Header");
		testRouteDefinition.setFilters(Arrays.asList(prefixPathFilterDefinition, redirectToFilterDefinition));

		PredicateDefinition hostRoutePredicateDefinition = new PredicateDefinition("Host=myhost.org");
		PredicateDefinition methodRoutePredicateDefinition = new PredicateDefinition("Method=GET");
		testRouteDefinition.setPredicates(Arrays.asList(hostRoutePredicateDefinition, methodRoutePredicateDefinition));
		return testRouteDefinition;
	}

	@EnableAutoConfiguration
	@SpringBootConfiguration
	@EnableReactiveMongoRepositories
	public static class TestConfig {

	}

}
