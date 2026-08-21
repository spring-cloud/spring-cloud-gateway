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
import org.springframework.cloud.gateway.filter.WebSessionStickyLoadBalancerFilter;
import org.springframework.web.server.ServerWebExchange;

import static org.springframework.cloud.gateway.support.GatewayToStringStyler.filterToStringCreator;

/**
 * A {@link GatewayFilterFactory} that enables WebSession-based sticky load balancing on a
 * per-route basis without requiring a custom URI scheme.
 *
 * <p>
 * When applied to a route, this filter sets the
 * {@link WebSessionStickyLoadBalancerFilter#IS_STICKY_ATTRIBUTE} exchange attribute to
 * {@code true} before the load-balancer filter runs. The
 * {@link org.springframework.cloud.gateway.filter.WebSessionStickyLoadBalancer} reads
 * this attribute to apply session affinity: the first request from a client selects an
 * instance via the delegate (round-robin by default) and records that selection in the
 * gateway {@link org.springframework.web.server.WebSession}; all subsequent requests from
 * that client session are routed to the same instance. If the instance is deregistered or
 * fails a health check, a fresh instance is chosen transparently and the session affinity
 * is updated.
 *
 * <p>
 * Unlike the {@code sticky://} scheme approach, this factory works with the standard
 * {@code lb://} URI scheme and composes naturally with other {@link GatewayFilterFactory}
 * filters on the same route. It requires {@link WebSessionStickyLoadBalancerFilter} to be
 * enabled:
 *
 * <pre>{@code
 * spring.cloud.gateway.global-filter.web-session-sticky-load-balancer.enabled=true
 * }</pre>
 *
 * Example route configuration:
 *
 * <pre>{@code
 * spring:
 *   cloud:
 *     gateway:
 *       server:
 *         webflux:
 *           routes:
 *             - id: legacy-service-route
 *               uri: lb://legacy-service
 *               predicates:
 *                 - Path=/legacy/**
 *               filters:
 *                 - WebSessionSticky
 *             - id: stateless-service-route
 *               uri: lb://my-service
 *               predicates:
 *                 - Path=/api/**
 * }</pre>
 *
 * In the example above, {@code /legacy/**} requests are pinned to a single instance of
 * {@code legacy-service} for the life of each client session, while {@code /api/**}
 * requests are distributed via normal round-robin.
 *
 * @author Beteab Gebru
 * @since 5.0.4
 * @see WebSessionStickyLoadBalancerFilter
 * @see org.springframework.cloud.gateway.filter.WebSessionStickyLoadBalancer
 */
public class WebSessionStickyGatewayFilterFactory extends AbstractGatewayFilterFactory<Object> {

	/**
	 * Creates a new {@code WebSessionStickyGatewayFilterFactory}.
	 */
	public WebSessionStickyGatewayFilterFactory() {
		super(Object.class);
	}

	@Override
	public GatewayFilter apply(final Object config) {
		return new GatewayFilter() {
			@Override
			public Mono<Void> filter(final ServerWebExchange exchange, final GatewayFilterChain chain) {
				exchange.getAttributes().put(WebSessionStickyLoadBalancerFilter.IS_STICKY_ATTRIBUTE, Boolean.TRUE);
				return chain.filter(exchange);
			}

			@Override
			public String toString() {
				return filterToStringCreator(WebSessionStickyGatewayFilterFactory.this).toString();
			}
		};
	}

}
