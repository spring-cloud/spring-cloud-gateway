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
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.web.server.ServerWebExchange;

import static org.springframework.cloud.gateway.support.GatewayToStringStyler.filterToStringCreator;

/**
 * @author Spencer Gibb
 */
public class AddResponseHeaderGatewayFilterFactory extends AbstractNameValueGatewayFilterFactory {

	@Override
	public GatewayFilter apply(NameValueConfig config) {
		return new GatewayFilter() {
			@Override
			public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
				return chain.filter(exchange).then(Mono.fromRunnable(() -> addHeader(exchange, config)));
			}

			@Override
			public String toString() {
				return filterToStringCreator(AddResponseHeaderGatewayFilterFactory.this)
						.append(config.getName(), config.getValue()).toString();
			}
		};
	}

	void addHeader(ServerWebExchange exchange, NameValueConfig config) {
		final String value = ServerWebExchangeUtils.expand(exchange, config.getValue());
		HttpHeaders headers = exchange.getResponse().getHeaders();
		// if response has been commited, no more response headers will bee added.
		if (!exchange.getResponse().isCommitted()) {
			headers.add(config.getName(), value);
		}
	}

}
