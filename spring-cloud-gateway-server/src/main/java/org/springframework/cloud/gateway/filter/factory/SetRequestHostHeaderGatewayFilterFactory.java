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

import java.util.Collections;
import java.util.List;

import reactor.core.publisher.Mono;

import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.server.ServerWebExchange;

import static org.springframework.cloud.gateway.support.GatewayToStringStyler.filterToStringCreator;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.PRESERVE_HOST_HEADER_ATTRIBUTE;

/**
 * @author Andrew Fitzgerald
 */
public class SetRequestHostHeaderGatewayFilterFactory
		extends AbstractGatewayFilterFactory<SetRequestHostHeaderGatewayFilterFactory.Config> {

	public SetRequestHostHeaderGatewayFilterFactory() {
		super(Config.class);
	}

	@Override
	public List<String> shortcutFieldOrder() {
		return Collections.singletonList("host");
	}

	@Override
	public GatewayFilter apply(Config config) {
		return new GatewayFilter() {
			@Override
			public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
				String value = ServerWebExchangeUtils.expand(exchange, config.getHost());

				ServerHttpRequest request = exchange.getRequest().mutate().headers(httpHeaders -> {
					httpHeaders.remove("Host");
					httpHeaders.add("Host", value);
				}).build();

				// Make sure the header we just set is preserved
				exchange.getAttributes().put(PRESERVE_HOST_HEADER_ATTRIBUTE, true);

				return chain.filter(exchange.mutate().request(request).build());
			}

			@Override
			public String toString() {
				return filterToStringCreator(SetRequestHostHeaderGatewayFilterFactory.this).append(config.getHost())
						.toString();
			}
		};
	}

	public static class Config {

		private String host;

		public String getHost() {
			return host;
		}

		public void setHost(String host) {
			this.host = host;
		}

	}

}
