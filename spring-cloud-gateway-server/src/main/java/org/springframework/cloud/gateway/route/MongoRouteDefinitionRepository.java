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
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

public class MongoRouteDefinitionRepository implements RouteDefinitionRepository {

	private static final Logger log = LoggerFactory.getLogger(MongoRouteDefinitionRepository.class);

	private final ReactiveMongoTemplate reactiveMongoTemplate;

	public MongoRouteDefinitionRepository(ReactiveMongoTemplate reactiveMongoTemplate) {
		this.reactiveMongoTemplate = reactiveMongoTemplate;
	}

	@Override
	public Flux<RouteDefinition> getRouteDefinitions() {
		return reactiveMongoTemplate.findAll(RouteDefinition.class).onErrorContinue((throwable, routeDefinitions) -> {
			if (log.isErrorEnabled()) {
				log.error("Error getting routes from MongoRouteDefinitionRepository: {}", throwable.toString(),
						throwable);
			}
		});
	}

	@Override
	public Mono<Void> save(Mono<RouteDefinition> route) {
		return route.flatMap(routeDefinition -> reactiveMongoTemplate.save(routeDefinition).doOnError(throwable -> {
			if (log.isErrorEnabled()) {
				log.error("Could not add route to MongoRouteDefinitionRepository: {}", routeDefinition);
			}
		}).flatMap(savedRouteDefinition -> {
			if (log.isDebugEnabled()) {
				log.debug("Saved Route to MongoRouteDefinitionRepository: {}", savedRouteDefinition);
			}
			return Mono.empty();
		}));
	}

	@Override
	public Mono<Void> delete(Mono<String> routeId) {
		return routeId.flatMap(id -> reactiveMongoTemplate
				.findAndRemove(new Query(Criteria.where("id").is(id)), RouteDefinition.class)
				.switchIfEmpty(Mono.defer(() -> Mono.error(new NotFoundException(
						String.format("Could not remove route from MongoRouteDefinitionRepository with id: %s", id)))))
				.flatMap(routeDefinition -> {
					if (log.isDebugEnabled()) {
						log.debug("Deleted route from MongoRouteDefinitionRepository: {}", routeDefinition);
					}
					return Mono.empty();
				}));
	}

}
