/*
 * Copyright 2002-2016 the original author or authors.
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
 */

package org.springframework.cloud.gateway.handler;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.cloud.gateway.config.Route;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.FilterFactory;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import org.springframework.web.server.WebHandler;
import org.springframework.web.server.handler.WebHandlerDecorator;

import reactor.core.publisher.Mono;

import static java.util.Collections.emptyList;
import static org.springframework.cloud.gateway.filter.GatewayFilter.GATEWAY_ROUTE_ATTR;

/**
 * WebHandler that delegates to a chain of {@link GatewayFilter} instances and then
 * to the target {@link WebHandler}.
 *
 * @author Rossen Stoyanchev
 * @since 5.0
 */
public class GatewayFilteringWebHandler extends WebHandlerDecorator {
	protected final Log logger = LogFactory.getLog(getClass());

	private final List<GatewayFilter> filters;
	private final Map<String, FilterFactory> filterDefinitions = new HashMap<>();

	public GatewayFilteringWebHandler(WebHandler targetHandler, List<GatewayFilter> filters,
									  List<FilterFactory> filterDefinitions) {
		super(targetHandler);
		this.filters = initList(filters);
		initList(filterDefinitions).forEach(def -> this.filterDefinitions.put(def.getName(), def));
	}

	private static <T> List<T> initList(List<T> list) {
		return (list != null ? list : emptyList());
	}

	/**
	 * Return a read-only list of the configured filters.
	 */
	public List<GatewayFilter> getFilters() {
		return this.filters;
	}

	@Override
	public Mono<Void> handle(ServerWebExchange exchange) {
		//TODO: probably a java 8 stream way of doing this
		ArrayList<GatewayFilter> routeFilters = new ArrayList<>(this.filters);
		Optional<Route> route = exchange.getAttribute(GATEWAY_ROUTE_ATTR);
		if (route.isPresent() && !route.get().getFilters().isEmpty()) {
			routeFilters.addAll(loadFilters(route.get()));
		}
		return new DefaultWebFilterChain(routeFilters, getDelegate()).filter(exchange);
	}

	private Collection<GatewayFilter> loadFilters(Route route) {
		return route.getFilters().stream()
				.map(definition -> {
					FilterFactory filter = this.filterDefinitions.get(definition.getName());
					if (filter == null) {
						throw new IllegalArgumentException("Unable to find FilterFactory with name " + definition.getName());
					}
					if (logger.isDebugEnabled()) {
						List<String> args;
						if (definition.getArgs() != null) {
							args = Arrays.asList(definition.getArgs());
						} else {
							args = Collections.emptyList();
						}
						logger.debug("Route " + route.getId() + " applying filter "+ definition.getValue()
								+ ", " + args + " to " + definition.getName());
					}
					return filter.apply(definition.getValue(), definition.getArgs());
				})
				.collect(Collectors.toList());
	}


	private static class DefaultWebFilterChain implements WebFilterChain {

		private int index;
		private final List<GatewayFilter> filters;
		private final WebHandler delegate;

		public DefaultWebFilterChain(List<GatewayFilter> filters, WebHandler delegate) {
			this.filters = filters;
			this.delegate = delegate;
		}

		@Override
		public Mono<Void> filter(ServerWebExchange exchange) {
			if (this.index < filters.size()) {
				GatewayFilter filter = filters.get(this.index++);
				return filter.filter(exchange, this);
			}
			else {
				return this.delegate.handle(exchange);
			}
		}
	}

}
