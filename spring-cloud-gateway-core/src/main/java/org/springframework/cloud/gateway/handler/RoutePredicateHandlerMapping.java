/*
 * Copyright 2013-2017 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package org.springframework.cloud.gateway.handler;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Predicate;

import org.springframework.cloud.gateway.model.RouteLocator;
import org.springframework.cloud.gateway.model.Route;
import org.springframework.cloud.gateway.model.PredicateDefinition;
import org.springframework.cloud.gateway.handler.predicate.RoutePredicate;
import org.springframework.web.reactive.handler.AbstractHandlerMapping;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebHandler;

import reactor.core.publisher.Mono;

import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_HANDLER_MAPPER_ATTR;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR;

/**
 * @author Spencer Gibb
 */
public class RoutePredicateHandlerMapping extends AbstractHandlerMapping {

	private final RouteLocator routeLocator;
	private final WebHandler webHandler;
	private final Map<String, RoutePredicate> routePredicates = new LinkedHashMap<>();
	//TODO: define semeantics for refresh (ie clearing and recalculating combinedPredicates)
	private final Map<String, Predicate<ServerWebExchange>> combinedPredicates = new ConcurrentHashMap<>();

	public RoutePredicateHandlerMapping(WebHandler webHandler, Map<String, RoutePredicate> routePredicates,
										RouteLocator routeLocator) {
		this.webHandler = webHandler;
		this.routeLocator = routeLocator;

		routePredicates.forEach((name, factory) -> {
			String key = normalizeName(name);
			if (this.routePredicates.containsKey(key)) {
				this.logger.warn("A RoutePredicate named "+ key
						+ " already exists, class: " + this.routePredicates.get(key)
						+ ". It will be overwritten.");
			}
			this.routePredicates.put(key, factory);
			if (logger.isInfoEnabled()) {
				logger.info("Loaded RoutePredicate [" + key + "]");
			}
		});

		setOrder(1);
	}

	private String normalizeName(String name) {
		return name.replace(RoutePredicate.class.getSimpleName(), "");
	}

	@Override
	protected Mono<?> getHandlerInternal(ServerWebExchange exchange) {
		exchange.getAttributes().put(GATEWAY_HANDLER_MAPPER_ATTR, getClass().getSimpleName());

		return lookupRoute(exchange)
				.log("TRACE")
				.then((Function<Route, Mono<?>>) r -> {
					if (logger.isDebugEnabled()) {
						logger.debug("Mapping [" + getExchangeDesc(exchange) + "] to " + r);
					}

					exchange.getAttributes().put(GATEWAY_ROUTE_ATTR, r);
					return Mono.just(webHandler);
				}).otherwiseIfEmpty(Mono.empty().then(() -> {
					if (logger.isTraceEnabled()) {
						logger.trace("No Route found for [" + getExchangeDesc(exchange) + "]");
					}
					return Mono.empty();
				}));
	}

	//TODO: get desc from factory?
	private String getExchangeDesc(ServerWebExchange exchange) {
		StringBuilder out = new StringBuilder();
		out.append("Exchange: ");
		out.append(exchange.getRequest().getMethod());
		out.append(" ");
		out.append(exchange.getRequest().getURI());
		return out.toString();
	}


	protected Mono<Route> lookupRoute(ServerWebExchange exchange) {
		return this.routeLocator.getRoutes()
				//TODO: cache predicate
				.map(route -> getRouteCombinedPredicates(route))
				.filter(rcp -> rcp.combinedPredicate.test(exchange))
				.next()
				//TODO: error handling
				.map(rcp -> {
					if (logger.isDebugEnabled()) {
						logger.debug("Route matched: " + rcp.route.getId());
					}
					validateRoute(rcp.route, exchange);
					return rcp.route;
				});

		/* TODO: trace logging
			if (logger.isTraceEnabled()) {
				logger.trace("Route did not match: " + route.getId());
			}*/
	}

	private RouteCombinedPredicates getRouteCombinedPredicates(Route route) {
		Predicate<ServerWebExchange> predicate = this.combinedPredicates
				.computeIfAbsent(route.getId(), k -> combinePredicates(route));

		return new RouteCombinedPredicates(route, predicate);
	}

	private class RouteCombinedPredicates {
		private Route route;
		private Predicate<ServerWebExchange> combinedPredicate;

		public RouteCombinedPredicates(Route route, Predicate<ServerWebExchange> combinedPredicate) {
			this.route = route;
			this.combinedPredicate = combinedPredicate;
		}
	}


	private Predicate<ServerWebExchange> combinePredicates(Route route) {
		List<PredicateDefinition> predicates = route.getPredicates();
		Predicate<ServerWebExchange> predicate = lookup(route, predicates.get(0));

		for (PredicateDefinition andPredicate : predicates.subList(1, predicates.size())) {
			Predicate<ServerWebExchange> found = lookup(route, andPredicate);
			predicate = predicate.and(found);
		}

		return predicate;
	}

	private Predicate<ServerWebExchange> lookup(Route route, PredicateDefinition predicate) {
		RoutePredicate found = this.routePredicates.get(predicate.getName());
		if (found == null) {
			throw new IllegalArgumentException("Unable to find RoutePredicate with name " + predicate.getName());
		}
		if (logger.isDebugEnabled()) {
			List<String> args;
			if (predicate.getArgs() != null) {
				args = Arrays.asList(predicate.getArgs());
			} else {
				args = Collections.emptyList();
			}
			logger.debug("Route " + route.getId() + " applying "
					+ args + " to " + predicate.getName());
		}
		return found.apply(predicate.getArgs());
	}

	/**
	 * Validate the given handler against the current request.
	 * <p>The default implementation is empty. Can be overridden in subclasses,
	 * for example to enforce specific preconditions expressed in URL mappings.
	 * @param route the Route object to validate
	 * @param exchange current exchange
	 * @throws Exception if validation failed
	 */
	@SuppressWarnings("UnusedParameters")
	protected void validateRoute(Route route, ServerWebExchange exchange) {
	}

}
