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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.springframework.cloud.gateway.support.NotFoundException;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.stereotype.Repository;

/**
 * @author Dennis Menge
 * @author lzhpo
 */
@Repository
public class RedisRouteDefinitionRepository implements RouteDefinitionRepository {

	private static final Logger log = LoggerFactory.getLogger(RedisRouteDefinitionRepository.class);

	/**
	 * Key prefix for RouteDefinition queries to redis.
	 */
	private static final String ROUTEDEFINITION_REDIS_KEY_PREFIX_QUERY = "routedefinition_";

	private ReactiveRedisTemplate<String, RouteDefinition> reactiveRedisTemplate;

	private ReactiveValueOperations<String, RouteDefinition> routeDefinitionReactiveValueOperations;

	public RedisRouteDefinitionRepository(ReactiveRedisTemplate<String, RouteDefinition> reactiveRedisTemplate) {
		this.reactiveRedisTemplate = reactiveRedisTemplate;
		this.routeDefinitionReactiveValueOperations = reactiveRedisTemplate.opsForValue();
	}

	@Override
	public Flux<RouteDefinition> getRouteDefinitions() {
		return reactiveRedisTemplate.scan(ScanOptions.scanOptions().match(createKey("*")).build())
			.flatMap(key -> reactiveRedisTemplate.opsForValue().get(key))
			.onErrorContinue((throwable, routeDefinition) -> {
				if (log.isErrorEnabled()) {
					log.error("get routes from redis error cause : {}", throwable.toString(), throwable);
				}
			});
	}

	@Override
	public Mono<Void> save(Mono<RouteDefinition> route) {
		return route.flatMap(routeDefinition -> routeDefinitionReactiveValueOperations
			.set(createKey(routeDefinition.getId()), routeDefinition)
			.flatMap(success -> {
				if (success) {
					return Mono.empty();
				}
				return Mono.defer(() -> Mono.error(new RuntimeException(
						String.format("Could not add route to redis repository: %s", routeDefinition))));
			}));
	}

	@Override
	public Mono<Void> delete(Mono<String> routeId) {
		return routeId.flatMap(id -> routeDefinitionReactiveValueOperations.delete(createKey(id)).flatMap(success -> {
			if (success) {
				return Mono.empty();
			}
			return Mono.defer(() -> Mono.error(new NotFoundException(
					String.format("Could not remove route from redis repository with id: %s", routeId))));
		}));
	}

	private String createKey(String routeId) {
		return ROUTEDEFINITION_REDIS_KEY_PREFIX_QUERY + routeId;
	}

}
