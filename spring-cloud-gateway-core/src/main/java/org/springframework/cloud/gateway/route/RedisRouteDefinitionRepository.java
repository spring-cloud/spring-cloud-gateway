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

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;

/**
 * @author Dennis Menge
 */
public class RedisRouteDefinitionRepository implements RouteDefinitionRepository {

	/**
	 * Key prefix for RouteDefinition queries to redis.
	 */
	private static final String ROUTEDEFINITION_REDIS_KEY_PREFIX_QUERY = "routedefinition_";

	private ReactiveRedisTemplate<String, RouteDefinition> reactiveRedisTemplate;

	private ReactiveValueOperations<String, RouteDefinition> routeDefinitionReactiveValueOperations;

	public RedisRouteDefinitionRepository(
			ReactiveRedisTemplate<String, RouteDefinition> reactiveRedisTemplate) {
		this.reactiveRedisTemplate = reactiveRedisTemplate;
		this.routeDefinitionReactiveValueOperations = reactiveRedisTemplate.opsForValue();
	}

	@Override
	public Flux<RouteDefinition> getRouteDefinitions() {
		return reactiveRedisTemplate.opsForSet()
				.scan(ROUTEDEFINITION_REDIS_KEY_PREFIX_QUERY + "*");
	}

	@Override
	public Mono<Void> save(Mono<RouteDefinition> route) {
		return route.flatMap(routeDefinition -> {
			routeDefinitionReactiveValueOperations.set(
					ROUTEDEFINITION_REDIS_KEY_PREFIX_QUERY + routeDefinition.getId(),
					routeDefinition);
			return Mono.empty();
		});
	}

	@Override
	public Mono<Void> delete(Mono<String> routeId) {
		return routeId.flatMap(id -> {
			routeDefinitionReactiveValueOperations.delete(id);
			return Mono.empty();
		});
	}

}
