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

package org.springframework.cloud.gateway.handler;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import reactor.core.publisher.Mono;

import org.springframework.cloud.gateway.event.RefreshRoutesEvent;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.filter.OrderedGatewayFilter;
import org.springframework.cloud.gateway.filter.factory.GatewayFilterFactory;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.context.ApplicationListener;
import org.springframework.core.DecoratingProxy;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.core.annotation.Order;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebHandler;

import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR;

/**
 * WebHandler that delegates to a chain of {@link GlobalFilter} instances and
 * {@link GatewayFilterFactory} instances then to the target {@link WebHandler}.
 *
 * @author Rossen Stoyanchev
 * @author Spencer Gibb
 * @author Yuxin Wang
 * @since 0.1
 */
public class FilteringWebHandler implements WebHandler, ApplicationListener<RefreshRoutesEvent> {

	protected static final Log logger = LogFactory.getLog(FilteringWebHandler.class);

	private final List<GatewayFilter> globalFilters;

	private final ConcurrentHashMap<Route, List<GatewayFilter>> routeFilterMap = new ConcurrentHashMap();

	private final boolean routeFilterCacheEnabled;

	@Deprecated
	public FilteringWebHandler(List<GlobalFilter> globalFilters) {
		this(globalFilters, false);
	}

	public FilteringWebHandler(List<GlobalFilter> globalFilters, boolean routeFilterCacheEnabled) {
		this.globalFilters = loadFilters(globalFilters);
		this.routeFilterCacheEnabled = routeFilterCacheEnabled;
	}

	/* for testing */ ConcurrentHashMap<Route, List<GatewayFilter>> getRouteFilterMap() {
		return routeFilterMap;
	}

	private static List<GatewayFilter> loadFilters(List<GlobalFilter> filters) {
		return filters.stream().map(filter -> {
			GatewayFilterAdapter gatewayFilter = new GatewayFilterAdapter(filter);
			if (filter instanceof Ordered ordered) {
				int order = ordered.getOrder();
				return new OrderedGatewayFilter(gatewayFilter, order);
			}
			else {
				Order order = AnnotationUtils.findAnnotation(filter.getClass(), Order.class);
				if (order != null) {
					return new OrderedGatewayFilter(gatewayFilter, order.value());
				}
			}
			return gatewayFilter;
		}).collect(Collectors.toList());
	}

	@Override
	public void onApplicationEvent(RefreshRoutesEvent event) {
		if (this.routeFilterCacheEnabled) {
			routeFilterMap.clear();
		}
	}

	@Override
	public Mono<Void> handle(ServerWebExchange exchange) {
		Route route = exchange.getRequiredAttribute(GATEWAY_ROUTE_ATTR);
		List<GatewayFilter> combined = getCombinedFilters(route);

		if (logger.isDebugEnabled()) {
			logger.debug("Sorted gatewayFilterFactories: " + combined);
		}

		return new DefaultGatewayFilterChain(combined).filter(exchange);
	}

	protected List<GatewayFilter> getCombinedFilters(Route route) {
		if (this.routeFilterCacheEnabled) {
			return routeFilterMap.computeIfAbsent(route, this::getAllFilters);
		}
		else {
			return getAllFilters(route);
		}
	}

	protected List<GatewayFilter> getAllFilters(Route route) {
		List<GatewayFilter> gatewayFilters = route.getFilters();
		List<GatewayFilter> combined = new ArrayList<>(this.globalFilters);
		combined.addAll(gatewayFilters);
		AnnotationAwareOrderComparator.sort(combined);
		return combined;
	}

	private static class DefaultGatewayFilterChain implements GatewayFilterChain {

		private final int index;

		private final List<GatewayFilter> filters;

		DefaultGatewayFilterChain(List<GatewayFilter> filters) {
			this.filters = filters;
			this.index = 0;
		}

		private DefaultGatewayFilterChain(DefaultGatewayFilterChain parent, int index) {
			this.filters = parent.getFilters();
			this.index = index;
		}

		public List<GatewayFilter> getFilters() {
			return filters;
		}

		@Override
		public Mono<Void> filter(ServerWebExchange exchange) {
			return Mono.defer(() -> {
				if (this.index < filters.size()) {
					GatewayFilter filter = filters.get(this.index);
					DefaultGatewayFilterChain chain = new DefaultGatewayFilterChain(this, this.index + 1);
					return filter.filter(exchange, chain);
				}
				else {
					return Mono.empty(); // complete
				}
			});
		}

	}

	private static class GatewayFilterAdapter implements GatewayFilter, DecoratingProxy {

		private final GlobalFilter delegate;

		GatewayFilterAdapter(GlobalFilter delegate) {
			this.delegate = delegate;
		}

		@Override
		public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
			return this.delegate.filter(exchange, chain);
		}

		@Override
		public String toString() {
			final StringBuilder sb = new StringBuilder("GatewayFilterAdapter{");
			sb.append("delegate=").append(delegate);
			sb.append('}');
			return sb.toString();
		}

		@Override
		public Class<?> getDecoratedClass() {
			return delegate.getClass();
		}

	}

}
