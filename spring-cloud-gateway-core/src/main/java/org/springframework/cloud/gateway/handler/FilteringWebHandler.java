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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.cloud.gateway.filter.factory.WebFilterFactory;
import org.springframework.cloud.gateway.model.FilterDefinition;
import org.springframework.cloud.gateway.model.Route;
import org.springframework.cloud.gateway.config.GatewayProperties;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.support.NameUtils;
import org.springframework.cloud.gateway.support.RefreshRoutesEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import org.springframework.tuple.Tuple;
import org.springframework.tuple.TupleBuilder;
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
 * {@link WebFilterFactory} instances then to the target {@link WebHandler}.
 *
 * @author Rossen Stoyanchev
 * @author Spencer Gibb
 * @since 0.1
 */
public class FilteringWebHandler extends WebHandlerDecorator {
	protected final Log logger = LogFactory.getLog(getClass());

	private final GatewayProperties gatewayProperties;
	private final List<GlobalFilter> globalFilters;
	private final Map<String, WebFilterFactory> webFilterFactories = new HashMap<>();

	private final ConcurrentMap<String, List<WebFilter>> combinedFiltersForRoute = new ConcurrentHashMap<>();

	public FilteringWebHandler(GatewayProperties gatewayProperties, List<GlobalFilter> globalFilters,
							   List<WebFilterFactory> webFilterFactories) {
		this(new EmptyWebHandler(), gatewayProperties, globalFilters, webFilterFactories);
	}

	public FilteringWebHandler(WebHandler targetHandler, GatewayProperties gatewayProperties, List<GlobalFilter> globalFilters,
							   List<WebFilterFactory> webFilterFactories) {
		super(targetHandler);
		this.gatewayProperties = gatewayProperties;
		this.globalFilters = initList(globalFilters);
		webFilterFactories.forEach(factory -> this.webFilterFactories.put(factory.name(), factory));
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


    @EventListener(RefreshRoutesEvent.class)
    /* for testing */ void handleRefresh() {
        this.combinedFiltersForRoute.clear();
    }

	@Override
	public Mono<Void> handle(ServerWebExchange exchange) {
		Optional<Route> route = exchange.getAttribute(GATEWAY_ROUTE_ATTR);
		List<WebFilter> webFilters = combineFiltersForRoute(route);

		logger.debug("Sorted webFilterFactories: "+ webFilters);

		return new DefaultWebFilterChain(webFilters, getDelegate()).filter(exchange);
	}

	public List<WebFilter> combineFiltersForRoute(Optional<Route> route) {
		if (!route.isPresent()) {
			return Collections.emptyList();
		}
		List<WebFilter> combinedFilters = this.combinedFiltersForRoute.get(route.get().getId());
		if (combinedFilters == null) {

			//TODO: probably a java 8 stream way of doing this
			combinedFilters = new ArrayList<>(loadFilters(this.globalFilters));

			//TODO: support option to apply defaults after route specific filters?
			if (!this.gatewayProperties.getDefaultFilters().isEmpty()) {
				combinedFilters.addAll(loadWebFilters("defaultFilters",
						this.gatewayProperties.getDefaultFilters()));
			}

			if (route.isPresent() && !route.get().getFilters().isEmpty()) {
				combinedFilters.addAll(loadWebFilters(route.get().getId(), route.get().getFilters()));
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

	private List<WebFilter> loadWebFilters(String id, List<FilterDefinition> filterDefinitions) {
		List<WebFilter> filters = filterDefinitions.stream()
				.map(definition -> {
					WebFilterFactory filter = this.webFilterFactories.get(definition.getName());
					if (filter == null) {
						throw new IllegalArgumentException("Unable to find WebFilterFactory with name " + definition.getName());
					}
					Map<String, String> args = definition.getArgs();
					if (logger.isDebugEnabled()) {
						logger.debug("Route " + id + " applying filter " + args + " to " + definition.getName());
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
