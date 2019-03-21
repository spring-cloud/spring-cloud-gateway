/*
 * Copyright 2018-2019 the original author or authors.
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

package org.springframework.cloud.gateway.rsocket.route;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.springframework.cloud.gateway.rsocket.server.GatewayExchange;

/**
 * @author Spencer Gibb
 */
public interface Routes {

	/** log. */
	Log log = LogFactory.getLog(Routes.class);

	Flux<Route> getRoutes();

	default Mono<Route> findRoute(GatewayExchange exchange) {
		return getRoutes()
				// individually filter routes so that filterWhen error delaying is not a
				// problem
				.concatMap(route -> Mono.just(route).filterWhen(r -> {
					// add the current route we are testing
					// TODO: exchange attributes
					// exchange.getAttributes().put(GATEWAY_PREDICATE_ROUTE_ATTR,
					// r.getId());
					return r.getPredicate().apply(exchange);
				})
						// instead of immediately stopping main flux due to error, log and
						// swallow it
						.doOnError(e -> log.error(
								"Error applying predicate for route: " + route.getId(),
								e))
						.onErrorResume(e -> Mono.empty()))
				.next().map(route -> {
					if (log.isDebugEnabled()) {
						log.debug("Route matched: " + route.getId());
					}
					return route;
				});
	}

}
