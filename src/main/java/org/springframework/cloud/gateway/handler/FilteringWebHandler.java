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
import org.springframework.cloud.gateway.api.Route;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.filter.route.RouteFilter;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import org.springframework.web.server.WebHandler;
import org.springframework.web.server.handler.WebHandlerDecorator;

import static java.util.Collections.emptyList;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR;

import reactor.core.publisher.Mono;

/**
 * WebHandler that delegates to a chain of {@link GlobalFilter} instances and
 * {@link RouteFilter} instances then to the target {@link WebHandler}.
 *
 * @author Rossen Stoyanchev
 * @author Spencer Gibb
 * @since 0.1
 */
public class FilteringWebHandler extends WebHandlerDecorator {
	protected final Log logger = LogFactory.getLog(getClass());

	private final List<GlobalFilter> globalFilters;
	private final Map<String, RouteFilter> routeFilters = new HashMap<>();

	private final Map<String, List<WebFilter>> combinedFiltersForRoute = new HashMap<>();

	public FilteringWebHandler(List<GlobalFilter> globalFilters,
							   Map<String, RouteFilter> routeFilters) {
		this(new EmptyWebHandler(), globalFilters, routeFilters);
	}

	public FilteringWebHandler(WebHandler targetHandler, List<GlobalFilter> globalFilters,
							   Map<String, RouteFilter> routeFilters) {
		super(targetHandler);
		this.globalFilters = initList(globalFilters);
		routeFilters.forEach((name, def) -> this.routeFilters.put(nornamlizeName(name), def));
	}

	private String nornamlizeName(String name) {
		return name.replace(RouteFilter.class.getSimpleName(), "");
	}

	private static <T> List<T> initList(List<T> list) {
		return (list != null ? list : emptyList());
	}

	/**
	 * Return a read-only list of the configured globalFilters.
	 */
	public List<GlobalFilter> getGlobalFilters() {
		return this.globalFilters;
	}

	@Override
	public Mono<Void> handle(ServerWebExchange exchange) {
		Optional<Route> route = exchange.getAttribute(GATEWAY_ROUTE_ATTR);
		List<WebFilter> routeFilters = combineFiltersForRoute(route);

		logger.debug("Sorted routeFilters: "+ routeFilters);

		return new DefaultWebFilterChain(routeFilters, getDelegate()).filter(exchange);
	}

	public List<WebFilter> combineFiltersForRoute(Optional<Route> route) {
		if (!route.isPresent()) {
			return Collections.emptyList();
		}
		List<WebFilter> combinedFilters = this.combinedFiltersForRoute.get(route.get().getId());
		if (combinedFilters == null) {

			//TODO: probably a java 8 stream way of doing this
			combinedFilters = new ArrayList<>(loadFilters(this.globalFilters));

			if (route.isPresent() && !route.get().getFilters().isEmpty()) {
				combinedFilters.addAll(loadRouteFilters(route.get()));
			}

			AnnotationAwareOrderComparator.sort(combinedFilters);
			this.combinedFiltersForRoute.put(route.get().getId(), combinedFilters);
		}
		return combinedFilters;
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

	private List<WebFilter> loadRouteFilters(Route route) {
		List<WebFilter> filters = route.getFilters().stream()
				.map(definition -> {
					RouteFilter filter = this.routeFilters.get(definition.getName());
					if (filter == null) {
						throw new IllegalArgumentException("Unable to find RouteFilter with name " + definition.getName());
					}
					if (logger.isDebugEnabled()) {
						List<String> args;
						if (definition.getArgs() != null) {
							args = Arrays.asList(definition.getArgs());
						} else {
							args = Collections.emptyList();
						}
						logger.debug("Route " + route.getId() + " applying filter " + args + " to " + definition.getName());
					}
					return filter.apply(definition.getArgs());
				})
				.collect(Collectors.toList());

		ArrayList<WebFilter> ordered = new ArrayList<>(filters.size());
		for (int i = 0; i < filters.size(); i++) {
			ordered.add(new OrderedWebFilter(filters.get(i), i+1));
		}

		return ordered;
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

	private static class DefaultWebFilterChain implements WebFilterChain {

		private int index;
		private final List<WebFilter> filters;
		private final WebHandler delegate;

		public DefaultWebFilterChain(List<WebFilter> filters, WebHandler delegate) {
			this.filters = filters;
			this.delegate = delegate;
		}

		@Override
		public Mono<Void> filter(ServerWebExchange exchange) {
			if (this.index < filters.size()) {
				WebFilter filter = filters.get(this.index++);
				return filter.filter(exchange, this);
			}
			else {
				return this.delegate.handle(exchange);
			}
		}
	}

	private static class EmptyWebHandler implements WebHandler {
		@Override
		public Mono<Void> handle(ServerWebExchange exchange) {
			return Mono.empty();
		}
	}

}
