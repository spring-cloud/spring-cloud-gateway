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

import java.util.Arrays;
import java.util.List;

import reactor.core.publisher.Mono;

import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.core.style.ToStringCreator;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.server.ServerWebExchange;

import static org.springframework.cloud.gateway.support.GatewayToStringStyler.filterToStringCreator;

/**
 * @author Tony Clarke
 */
public class MapRequestHeaderGatewayFilterFactory
		extends AbstractGatewayFilterFactory<MapRequestHeaderGatewayFilterFactory.Config> {

	/**
	 * From Header key.
	 */
	public static final String FROM_HEADER_KEY = "fromHeader";

	/**
	 * To Header key.
	 */
	public static final String TO_HEADER_KEY = "toHeader";

	public MapRequestHeaderGatewayFilterFactory() {
		super(Config.class);
	}

	public List<String> shortcutFieldOrder() {
		return Arrays.asList(FROM_HEADER_KEY, TO_HEADER_KEY);
	}

	@Override
	public GatewayFilter apply(MapRequestHeaderGatewayFilterFactory.Config config) {
		return new GatewayFilter() {
			@Override
			public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
				if (!exchange.getRequest().getHeaders().containsKey(config.getFromHeader())) {
					return chain.filter(exchange);
				}
				List<String> headerValues = exchange.getRequest().getHeaders().get(config.getFromHeader());

				ServerHttpRequest request = exchange.getRequest().mutate()
						.headers(i -> i.addAll(config.getToHeader(), headerValues)).build();

				return chain.filter(exchange.mutate().request(request).build());
			}

			@Override
			public String toString() {
				// @formatter:off
				return filterToStringCreator(MapRequestHeaderGatewayFilterFactory.this)
						.append(FROM_HEADER_KEY, config.getFromHeader())
						.append(TO_HEADER_KEY, config.getToHeader())
						.toString();
				// @formatter:on
			}
		};
	}

	public static class Config {

		private String fromHeader;

		private String toHeader;

		public String getFromHeader() {
			return this.fromHeader;
		}

		public Config setFromHeader(String fromHeader) {
			this.fromHeader = fromHeader;
			return this;
		}

		public String getToHeader() {
			return this.toHeader;
		}

		public Config setToHeader(String toHeader) {
			this.toHeader = toHeader;
			return this;
		}

		@Override
		public String toString() {
			// @formatter:off
			return new ToStringCreator(this)
					.append("fromHeader", fromHeader)
					.append("toHeader", toHeader)
					.toString();
			// @formatter:on
		}

	}

}
