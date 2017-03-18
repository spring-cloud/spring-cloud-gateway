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
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.cloud.gateway.filter.OrderedWebFilter;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.config.GatewayProperties;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.filter.factory.WebFilterFactory;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import org.springframework.web.server.WebHandler;
import org.springframework.web.server.handler.WebHandlerDecorator;

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

	private final List<WebFilter> globalFilters;

	public FilteringWebHandler(List<GlobalFilter> globalFilters) {
		this(new EmptyWebHandler(), globalFilters);
	}

	public FilteringWebHandler(WebHandler targetHandler, List<GlobalFilter> globalFilters) {
		super(targetHandler);
		this.globalFilters = loadFilters(globalFilters);
	}

	private static List<WebFilter> loadFilters(List<GlobalFilter> filters) {
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

    /* TODO: relocate @EventListener(RefreshRoutesEvent.class)
    void handleRefresh() {
        this.combinedFiltersForRoute.clear();
    }*/

	@Override
	public Mono<Void> handle(ServerWebExchange exchange) {
		Optional<Route> route = exchange.getAttribute(GATEWAY_ROUTE_ATTR);
		List<WebFilter> webFilters = route.get().getWebFilters();

		List<WebFilter> combined = new ArrayList<>(this.globalFilters);
		combined.addAll(webFilters);
		//TODO: needed or cached?
		AnnotationAwareOrderComparator.sort(combined);

		logger.debug("Sorted webFilterFactories: "+ combined);

		return new DefaultWebFilterChain(combined, getDelegate()).filter(exchange);
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


	private static class EmptyWebHandler implements WebHandler {
		@Override
		public Mono<Void> handle(ServerWebExchange exchange) {
			return Mono.empty();
		}
	}

}
