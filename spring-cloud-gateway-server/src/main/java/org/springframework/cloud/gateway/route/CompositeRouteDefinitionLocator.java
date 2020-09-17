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

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import org.springframework.util.AlternativeJdkIdGenerator;
import org.springframework.util.IdGenerator;

/**
 * @author Spencer Gibb
 */
public class CompositeRouteDefinitionLocator implements RouteDefinitionLocator {

	private static final Log log = LogFactory.getLog(CompositeRouteDefinitionLocator.class);

	private final Flux<RouteDefinitionLocator> delegates;

	private final IdGenerator idGenerator;

	public CompositeRouteDefinitionLocator(Flux<RouteDefinitionLocator> delegates) {
		this(delegates, new AlternativeJdkIdGenerator());
	}

	public CompositeRouteDefinitionLocator(Flux<RouteDefinitionLocator> delegates, IdGenerator idGenerator) {
		this.delegates = delegates;
		this.idGenerator = idGenerator;
	}

	@Override
	public Flux<RouteDefinition> getRouteDefinitions() {
		return this.delegates.flatMapSequential(RouteDefinitionLocator::getRouteDefinitions)
				.flatMap(routeDefinition -> {
					if (routeDefinition.getId() == null) {
						return randomId().map(id -> {
							routeDefinition.setId(id);
							if (log.isDebugEnabled()) {
								log.debug("Id set on route definition: " + routeDefinition);
							}
							return routeDefinition;
						});
					}
					return Mono.just(routeDefinition);
				});
	}

	protected Mono<String> randomId() {
		return Mono.fromSupplier(idGenerator::toString).publishOn(Schedulers.boundedElastic());
	}

}
