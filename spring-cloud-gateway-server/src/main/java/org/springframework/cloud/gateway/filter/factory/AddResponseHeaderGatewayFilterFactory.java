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

import java.util.Arrays;
import java.util.List;

import reactor.core.publisher.Mono;

import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.core.style.ToStringCreator;
import org.springframework.http.HttpHeaders;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;

import static org.springframework.cloud.gateway.support.GatewayToStringStyler.filterToStringCreator;

/**
 * @author Spencer Gibb
 */
public class AddResponseHeaderGatewayFilterFactory extends AbstractNameValueGatewayFilterFactory {

	private static final String OVERRIDE_KEY = "override";

	@Override
	public Class getConfigClass() {
		return Config.class;
	}

	@Override
	public NameValueConfig newConfig() {
		return new Config();
	}

	@Override
	public List<String> shortcutFieldOrder() {
		return Arrays.asList(GatewayFilter.NAME_KEY, GatewayFilter.VALUE_KEY, OVERRIDE_KEY);
	}

	@Override
	public GatewayFilter apply(NameValueConfig config) {
		return new GatewayFilter() {
			@Override
			public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
				return chain.filter(exchange).then(Mono.fromRunnable(() -> addHeader(exchange, config)));
			}

			@Override
			public String toString() {
				if (config instanceof Config) {
					return filterToStringCreator(AddResponseHeaderGatewayFilterFactory.this)
						.append(GatewayFilter.NAME_KEY, config.getName())
						.append(GatewayFilter.VALUE_KEY, config.getValue())
						.append(OVERRIDE_KEY, ((Config) config).isOverride())
						.toString();
				}
				return filterToStringCreator(AddResponseHeaderGatewayFilterFactory.this)
					.append(config.getName(), config.getValue())
					.toString();
			}
		};
	}

	void addHeader(ServerWebExchange exchange, NameValueConfig config) {
		// if response has been commited, no more response headers will bee added.
		if (!exchange.getResponse().isCommitted()) {
			final String value = ServerWebExchangeUtils.expand(exchange, config.getValue());
			HttpHeaders headers = exchange.getResponse().getHeaders();

			boolean override = true; // default is true
			if (config instanceof Config) {
				override = ((Config) config).isOverride();
			}

			if (override) {
				headers.add(config.getName(), value);
			}
			else {
				boolean headerIsMissingOrBlank = headers.getOrEmpty(config.getName())
					.stream()
					.allMatch(h -> !StringUtils.hasText(h));
				if (headerIsMissingOrBlank) {
					headers.add(config.getName(), value);
				}
			}
		}
	}

	public static class Config extends AbstractNameValueGatewayFilterFactory.NameValueConfig {

		private boolean override = true;

		public boolean isOverride() {
			return override;
		}

		public Config setOverride(boolean override) {
			this.override = override;
			return this;
		}

		@Override
		public String toString() {
			return new ToStringCreator(this).append(NAME_KEY, name)
				.append(VALUE_KEY, value)
				.append(OVERRIDE_KEY, override)
				.toString();
		}

	}

}
