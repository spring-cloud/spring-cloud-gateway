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

package org.springframework.cloud.gateway.route;

import java.time.Duration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import org.springframework.cloud.gateway.support.NotFoundException;

public class InMemoryRouteDefinitionRepositoryTests {

	private InMemoryRouteDefinitionRepository repository;

	@BeforeEach
	public void setUp() throws Exception {
		repository = new InMemoryRouteDefinitionRepository();
	}

	@Test
	public void shouldProtectRoutesAgainstConcurrentModificationException() {
		Flux<Void> createRoutes = Flux.just(createRoute("foo1"), createRoute("foo2"), createRoute("foo3"))
				.flatMap(repository::save);

		StepVerifier.create(createRoutes).verifyComplete();

		Flux<RouteDefinition> readRoutesWithDelay = repository.getRouteDefinitions()
				.delayElements(Duration.ofMillis(100));

		Mono<Void> createAnotherRoute = repository.save(createRoute("bar"));

		StepVerifier.withVirtualTime(() -> readRoutesWithDelay).expectSubscription().expectNextCount(1)
				.then(createAnotherRoute::subscribe).thenAwait().expectNextCount(2).verifyComplete();
	}

	@Test
	public void shouldValidateRouteIdOnCreate() {
		Mono<RouteDefinition> emptyRoute = Mono.just(new RouteDefinition());
		StepVerifier.create(repository.save(emptyRoute)).verifyError(IllegalArgumentException.class);
	}

	@Test
	public void shouldCreateRoute() {
		StepVerifier.create(repository.save(createRoute("foo"))).verifyComplete();

		StepVerifier.create(repository.getRouteDefinitions()).expectNextCount(1).verifyComplete();
	}

	@Test
	public void shouldDeleteRoute() {
		Flux<Void> createRoutes = Flux.just(createRoute("foo1"), createRoute("foo2"), createRoute("foo3"))
				.flatMap(repository::save);

		StepVerifier.create(createRoutes).verifyComplete();

		Mono<Void> deleteRoute = repository.delete(Mono.just("foo2"));

		StepVerifier.create(deleteRoute).verifyComplete();

		StepVerifier.create(repository.getRouteDefinitions()).expectNextCount(2).verifyComplete();
	}

	@Test
	public void shouldThrownNotFoundWhenDeleteInvalidRoute() {
		StepVerifier.create(repository.delete(Mono.just("x"))).verifyError(NotFoundException.class);
	}

	private Mono<RouteDefinition> createRoute(String id) {
		RouteDefinition routeDefinition = new RouteDefinition();
		routeDefinition.setId(id);
		return Mono.just(routeDefinition);
	}

}
