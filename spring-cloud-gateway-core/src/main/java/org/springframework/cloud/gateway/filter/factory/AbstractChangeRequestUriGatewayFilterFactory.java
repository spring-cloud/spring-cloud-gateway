/*
 * Copyright 2013-2018 the original author or authors.
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
 *
 */

package org.springframework.cloud.gateway.filter.factory;

import java.net.URI;
import java.util.Map;
import java.util.Optional;

import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.OrderedGatewayFilter;
import org.springframework.cloud.gateway.filter.RouteToRequestUrlFilter;
import org.springframework.web.server.ServerWebExchange;

import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_REQUEST_URL_ATTR;

/**
 * This filter changes the request uri by
 * {@link #determineRequestUri(ServerWebExchange, T)} logic.
 *
 * @author Toshiaki Maki
 */
public abstract class AbstractChangeRequestUriGatewayFilterFactory<T>
		extends AbstractGatewayFilterFactory<T> {
	private final int order;

	public AbstractChangeRequestUriGatewayFilterFactory(Class<T> clazz, int order) {
		super(clazz);
		this.order = order;
	}

	public AbstractChangeRequestUriGatewayFilterFactory(Class<T> clazz) {
		this(clazz, RouteToRequestUrlFilter.ROUTE_TO_URL_FILTER_ORDER + 1);
	}

	protected abstract Optional<URI> determineRequestUri(ServerWebExchange exchange,
			T config);

	public GatewayFilter apply(T config) {
		return new OrderedGatewayFilter((exchange, chain) -> {
			Optional<URI> uri = this.determineRequestUri(exchange, config);
			uri.ifPresent(u -> {
				Map<String, Object> attributes = exchange.getAttributes();
				attributes.put(GATEWAY_REQUEST_URL_ATTR, u);
			});
			return chain.filter(exchange);
		}, this.order);
	}
}
