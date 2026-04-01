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

package org.springframework.cloud.gateway.filter.factory;

import reactor.core.publisher.Mono;

import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;

import static org.springframework.cloud.gateway.support.GatewayToStringStyler.filterToStringCreator;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_REQUEST_URL_ATTR;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.addOriginalRequestUrl;

/**
 * Strips the request context path before later path-manipulating filters run.
 *
 * @author Garvit Joshi
 */
public class StripContextPathGatewayFilterFactory extends AbstractGatewayFilterFactory<Object> {

	public GatewayFilter apply() {
		return apply(new Object());
	}

	@Override
	public GatewayFilter apply(Object config) {
		return new GatewayFilter() {
			@Override
			public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
				ServerHttpRequest request = exchange.getRequest();
				String contextPath = request.getPath().contextPath().value();
				if (!StringUtils.hasText(contextPath)) {
					return chain.filter(exchange);
				}

				addOriginalRequestUrl(exchange, request.getURI());

				String newPath = request.getPath().pathWithinApplication().value();
				if (!StringUtils.hasText(newPath)) {
					newPath = "/";
				}

				ServerHttpRequest newRequest = request.mutate().contextPath("").path(newPath).build();
				exchange.getAttributes().put(GATEWAY_REQUEST_URL_ATTR, newRequest.getURI());

				return chain.filter(exchange.mutate().request(newRequest).build());
			}

			@Override
			public String toString() {
				return filterToStringCreator(StripContextPathGatewayFilterFactory.this).toString();
			}
		};
	}

}
