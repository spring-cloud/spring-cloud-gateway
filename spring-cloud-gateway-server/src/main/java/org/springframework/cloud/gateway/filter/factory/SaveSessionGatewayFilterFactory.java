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

package org.springframework.cloud.gateway.filter.factory;

import reactor.core.publisher.Mono;

import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebSession;

import static org.springframework.cloud.gateway.support.GatewayToStringStyler.filterToStringCreator;

/**
 * Save the current {@link WebSession} before executing the rest of the
 * {@link org.springframework.cloud.gateway.filter.GatewayFilterChain}.
 *
 * Filter is very useful for situation where the WebSession is lazy (e.g. Spring Session
 * MongoDB) and making a remote call requires that {@link WebSession#save()} be called
 * before the remote call is made.
 *
 * @author Greg Turnquist
 */
public class SaveSessionGatewayFilterFactory extends AbstractGatewayFilterFactory<Object> {

	@Override
	public GatewayFilter apply(Object config) {
		return new GatewayFilter() {
			@Override
			public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
				return exchange.getSession().map(WebSession::save).then(chain.filter(exchange));
			}

			@Override
			public String toString() {
				return filterToStringCreator(SaveSessionGatewayFilterFactory.this).toString();
			}
		};
	}

}
