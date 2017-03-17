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

package org.springframework.cloud.gateway.support;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.cloud.gateway.api.RouteDefinitionLocator;
import org.springframework.cloud.gateway.api.RouteLocator;
import org.springframework.cloud.gateway.config.GatewayProperties;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.filter.factory.WebFilterFactory;
import org.springframework.cloud.gateway.handler.predicate.RequestPredicateFactory;
import org.springframework.cloud.gateway.model.FilterDefinition;
import org.springframework.cloud.gateway.model.PredicateDefinition;
import org.springframework.cloud.gateway.model.Route;
import org.springframework.cloud.gateway.model.RouteDefinition;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import org.springframework.tuple.Tuple;
import org.springframework.tuple.TupleBuilder;
import org.springframework.web.reactive.function.server.RequestPredicate;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static java.util.Collections.emptyList;

/**
 * {@link RouteLocator} that loads routes from a {@link RouteDefinitionLocator}
 * @author Spencer Gibb
 */
public class RouteDefinitionRouteLocator implements RouteLocator {
	protected final Log logger = LogFactory.getLog(getClass());

	private final RouteDefinitionLocator routeDefinitionLocator;
	private final Map<String, RequestPredicateFactory> requestPredicates = new LinkedHashMap<>();
	private final List<GlobalFilter> globalFilters;
	private final Map<String, WebFilterFactory> webFilterFactories = new HashMap<>();
	private final GatewayProperties gatewayProperties;

	public RouteDefinitionRouteLocator(RouteDefinitionLocator routeDefinitionLocator,
									   List<RequestPredicateFactory> requestPredicates,
									   List<GlobalFilter> globalFilters,
									   List<WebFilterFactory> webFilterFactories,
									   GatewayProperties gatewayProperties) {
		this.routeDefinitionLocator = routeDefinitionLocator;
		initFactories(requestPredicates);
		this.globalFilters = initList(globalFilters);
		webFilterFactories.forEach(factory -> this.webFilterFactories.put(factory.name(), factory));
		this.gatewayProperties = gatewayProperties;
	}

	private static <T> List<T> initList(List<T> list) {
		return (list != null ? list : emptyList());
	}

	private void initFactories(List<RequestPredicateFactory> requestPredicates) {
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
	}

	@Override
	public Flux<Route> getRoutes() {
		return this.routeDefinitionLocator.getRouteDefinitions()
				.map(this::convertToRoute)
				//TODO: error handling
				.map(route -> {
					if (logger.isDebugEnabled()) {
						logger.debug("RouteDefinition matched: " + route.getId());
					}
					return route;
				});


		/* TODO: trace logging
			if (logger.isTraceEnabled()) {
				logger.trace("RouteDefinition did not match: " + routeDefinition.getId());
			}*/
	}


	private Route convertToRoute(RouteDefinition routeDefinition) {
		RequestPredicate requestPredicate = combinePredicates(routeDefinition);
		List<WebFilter> webFilters = getFilters(routeDefinition);

		return Route.builder(routeDefinition)
				.requestPredicate(requestPredicate)
				.webFilters(webFilters)
				.build();
	}

	private Collection<WebFilter> loadFilters(List<GlobalFilter> filters) {
		return filters.stream()
				.map(filter -> {
					WebFilterAdapter webFilter = new WebFilterAdapter(filter);
					if (filter instanceof Ordered) {
						int order = ((Ordered) filter).getOrder();
						return new OrderedWebFilter(webFilter, order);
					}
					return webFilter;
				}).collect(Collectors.toList());
	}

	private List<WebFilter> loadWebFilters(String id, List<FilterDefinition> filterDefinitions) {
		List<WebFilter> filters = filterDefinitions.stream()
				.map(definition -> {
					WebFilterFactory filter = this.webFilterFactories.get(definition.getName());
					if (filter == null) {
						throw new IllegalArgumentException("Unable to find WebFilterFactory with name " + definition.getName());
					}
					Map<String, String> args = definition.getArgs();
					if (logger.isDebugEnabled()) {
						logger.debug("RouteDefinition " + id + " applying filter " + args + " to " + definition.getName());
					}

					//TODO: move Tuple building to common class, see RequestPredicateFactory.lookup
					TupleBuilder builder = TupleBuilder.tuple();

					List<String> argNames = filter.argNames();
					if (!argNames.isEmpty()) {
						// ensure size is the same for key replacement later
						if (filter.validateArgs() && args.size() != argNames.size()) {
							throw new IllegalArgumentException("Wrong number of arguments. Expected " + argNames
									+ " " + argNames + ". Found " + args.size() + " " + args + "'");
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

					if (filter.validateArgs()) {
						for (String name : argNames) {
							if (!tuple.hasFieldName(name)) {
								throw new IllegalArgumentException("Missing argument '" + name + "'. Given " + tuple);
							}
						}
					}

					return filter.apply(tuple);
				})
				.collect(Collectors.toList());

		ArrayList<WebFilter> ordered = new ArrayList<>(filters.size());
		for (int i = 0; i < filters.size(); i++) {
			ordered.add(new OrderedWebFilter(filters.get(i), i+1));
		}

		return ordered;
	}

	private List<WebFilter> getFilters(RouteDefinition routeDefinition) {
		//TODO: probably a java 8 stream way of doing this
		List<WebFilter> combined = new ArrayList<>(loadFilters(this.globalFilters));

		//TODO: support option to apply defaults after route specific filters?
		if (!this.gatewayProperties.getDefaultFilters().isEmpty()) {
			combined.addAll(loadWebFilters("defaultFilters",
					this.gatewayProperties.getDefaultFilters()));
		}

		if (!routeDefinition.getFilters().isEmpty()) {
			combined.addAll(loadWebFilters(routeDefinition.getId(), routeDefinition.getFilters()));
		}

		AnnotationAwareOrderComparator.sort(combined);
		return combined;
	}

	private RequestPredicate combinePredicates(RouteDefinition routeDefinition) {
		List<PredicateDefinition> predicates = routeDefinition.getPredicates();
		RequestPredicate predicate = lookup(routeDefinition, predicates.get(0));

		for (PredicateDefinition andPredicate : predicates.subList(1, predicates.size())) {
			RequestPredicate found = lookup(routeDefinition, andPredicate);
			predicate = predicate.and(found);
		}

		return predicate;
	}

	private RequestPredicate lookup(RouteDefinition routeDefinition, PredicateDefinition predicate) {
		RequestPredicateFactory found = this.requestPredicates.get(predicate.getName());
		if (found == null) {
			throw new IllegalArgumentException("Unable to find RequestPredicateFactory with name " + predicate.getName());
		}
		Map<String, String> args = predicate.getArgs();
		if (logger.isDebugEnabled()) {
			logger.debug("RouteDefinition " + routeDefinition.getId() + " applying "
					+ args + " to " + predicate.getName());
		}

		TupleBuilder builder = TupleBuilder.tuple();

		List<String> argNames = found.argNames();
		if (!argNames.isEmpty()) {
			if (!argNames.isEmpty()) {
				// ensure size is the same for key replacement later
				if (found.validateArgs() && args.size() != argNames.size()) {
					throw new IllegalArgumentException("Wrong number of arguments. Expected " + argNames
							+ " " + argNames + ". Found " + args.size() + " " + args + "'");
				}
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

		if (found.validateArgs()) {
			for (String name : argNames) {
				if (!tuple.hasFieldName(name)) {
					throw new IllegalArgumentException("Missing argument '" + name + "'. Given " + tuple);
				}
			}
		}

		return found.apply(tuple);
	}


	private static class WebFilterAdapter implements WebFilter {

		private final GlobalFilter delegate;

		public WebFilterAdapter(GlobalFilter delegate) {
			this.delegate = delegate;
		}

		@Override
		public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
			return this.delegate.filter(exchange, chain);
		}

		@Override
		public String toString() {
			final StringBuilder sb = new StringBuilder("WebFilterAdapter{");
			sb.append("delegate=").append(delegate);
			sb.append('}');
			return sb.toString();
		}
	}

	public class OrderedWebFilter implements WebFilter, Ordered {

		private final WebFilter delegate;
		private final int order;

		public OrderedWebFilter(WebFilter delegate, int order) {
			this.delegate = delegate;
			this.order = order;
		}

		@Override
		public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
			return this.delegate.filter(exchange, chain);
		}

		@Override
		public int getOrder() {
			return this.order;
		}

		@Override
		public String toString() {
			final StringBuilder sb = new StringBuilder("OrderedWebFilter{");
			sb.append("delegate=").append(delegate);
			sb.append(", order=").append(order);
			sb.append('}');
			return sb.toString();
		}
	}
}
