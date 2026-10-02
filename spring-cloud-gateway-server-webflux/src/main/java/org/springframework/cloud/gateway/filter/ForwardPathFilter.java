/*
 * Copyright 2013-present the original author or authors.
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

package org.springframework.cloud.gateway.filter;

import java.net.URI;

import reactor.core.publisher.Mono;

import org.springframework.cloud.gateway.route.Route;
import org.springframework.core.Ordered;
import org.springframework.web.server.ServerWebExchange;

import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.addOriginalRequestUrl;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.isAlreadyRouted;

/**
 * Filter to set the path in the request URI if the {@link Route} URI has the scheme
 * <code>forward</code>.
 *
 * @author Ryan Baxter
 */
public class ForwardPathFilter implements GlobalFilter, Ordered {

	@Override
	public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
		Route route = exchange.getAttribute(GATEWAY_ROUTE_ATTR);
		if (route != null) {
			URI routeUri = route.getUri();
			String scheme = routeUri.getScheme();
			if (isAlreadyRouted(exchange) || !"forward".equals(scheme)) {
				return chain.filter(exchange);
			}
			// Record the pre-forward URL so downstream handlers can retrieve it via
			// GATEWAY_ORIGINAL_REQUEST_URL_ATTR, matching every other filter that
			// rewrites the request path (e.g. SetPathGatewayFilterFactory,
			// RewritePathGatewayFilterFactory). Without this, forward:// routes never
			// populated the attribute despite it being documented behavior.
			// See https://github.com/spring-cloud/spring-cloud-gateway/issues/4053
			addOriginalRequestUrl(exchange, exchange.getRequest().getURI());
			exchange = exchange.mutate()
				.request(exchange.getRequest().mutate().path(routeUri.getPath()).build())
				.build();

		}
		return chain.filter(exchange);
	}

	@Override
	public int getOrder() {
		return 0;
	}

}
