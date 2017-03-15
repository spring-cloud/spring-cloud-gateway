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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import org.springframework.cloud.gateway.api.RouteLocator;
import org.springframework.cloud.gateway.handler.predicate.RequestPredicateFactory;
import org.springframework.cloud.gateway.model.PredicateDefinition;
import org.springframework.cloud.gateway.model.Route;
import org.springframework.cloud.gateway.support.NameUtils;
import org.springframework.tuple.Tuple;
import org.springframework.tuple.TupleBuilder;
import org.springframework.web.reactive.function.server.PublicDefaultServerRequest;
import org.springframework.web.reactive.function.server.RequestPredicate;
import org.springframework.web.reactive.handler.AbstractHandlerMapping;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebHandler;

import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_HANDLER_MAPPER_ATTR;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR;

import reactor.core.publisher.Mono;

/**
 * @author Spencer Gibb
 */
public class RequestPredicateHandlerMapping extends AbstractHandlerMapping {

	private final RouteLocator routeLocator;
	private final WebHandler webHandler;
	private final Map<String, RequestPredicateFactory> requestPredicates = new LinkedHashMap<>();
	//TODO: define semeantics for refresh (ie clearing and recalculating combinedPredicates)
	private final Map<String, RequestPredicate> combinedPredicates = new ConcurrentHashMap<>();

	public RequestPredicateHandlerMapping(WebHandler webHandler, List<RequestPredicateFactory> requestPredicates,
										  RouteLocator routeLocator) {
		this.webHandler = webHandler;
		this.routeLocator = routeLocator;

		requestPredicates.forEach(factory -> {
			String key = factory.name();
			if (this.requestPredicates.containsKey(key)) {
				this.logger.warn("A RequestPredicateFactory named "+ key
						+ " already exists, class: " + this.requestPredicates.get(key)
						+ ". It will be overwritten.");
			}
			this.requestPredicates.put(key, factory);
			if (logger.isInfoEnabled()) {
				logger.info("Loaded RequestPredicateFactory [" + key + "]");
			}
		});

		setOrder(1);
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
				.map(this::getRouteCombinedPredicates)
				//TODO: fix PublicDefaultServerRequest?
				.filter(rcp -> rcp.combinedPredicate.test(new PublicDefaultServerRequest(exchange)))
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
		RequestPredicate predicate = this.combinedPredicates
				.computeIfAbsent(route.getId(), k -> combinePredicates(route));

		return new RouteCombinedPredicates(route, predicate);
	}

	private class RouteCombinedPredicates {
		private Route route;
		private RequestPredicate combinedPredicate;

		public RouteCombinedPredicates(Route route, RequestPredicate combinedPredicate) {
			this.route = route;
			this.combinedPredicate = combinedPredicate;
		}
	}

	private RequestPredicate combinePredicates(Route route) {
		List<PredicateDefinition> predicates = route.getPredicates();
		RequestPredicate predicate = lookup(route, predicates.get(0));

		for (PredicateDefinition andPredicate : predicates.subList(1, predicates.size())) {
			RequestPredicate found = lookup(route, andPredicate);
			predicate = predicate.and(found);
		}

		return predicate;
	}

	//TODO: decouple from HandlerMapping?
	private RequestPredicate lookup(Route route, PredicateDefinition predicate) {
		RequestPredicateFactory found = this.requestPredicates.get(predicate.getName());
		if (found == null) {
			throw new IllegalArgumentException("Unable to find RequestPredicateFactory with name " + predicate.getName());
		}
		Map<String, String> args = predicate.getArgs();
		if (logger.isDebugEnabled()) {
			logger.debug("Route " + route.getId() + " applying "
					+ args + " to " + predicate.getName());
		}

		TupleBuilder builder = TupleBuilder.tuple();

		List<String> argNames = found.argNames();
		if (!argNames.isEmpty()) {
			// ensure size is the same for key replacement later
			if (found.validateArgSize() && args.size() != argNames.size()) {
				throw new IllegalArgumentException("Wrong number of arguments. Expected " + argNames
						+ " " + argNames + ". Found "+ args.size() +" " + args +"'");
			}
		}

		int entryIdx = 0;
		for (Map.Entry<String, String> entry : args.entrySet()) {
			String key = entry.getKey();

			// RequestPredicateFactory has name hints and this has a fake key name
			// replace with the matching key hint
			if (key.startsWith(NameUtils.GENERATED_NAME_PREFIX) && !argNames.isEmpty()
					&& entryIdx < args.size()) {
				key = argNames.get(entryIdx);
			}

			builder.put(key, entry.getValue());
			entryIdx++;
		}

		Tuple tuple = builder.build();
		return found.apply(tuple);
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
