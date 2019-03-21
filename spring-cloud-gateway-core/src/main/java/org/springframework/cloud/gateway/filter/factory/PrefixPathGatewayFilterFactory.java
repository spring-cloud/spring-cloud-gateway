/*
 * Copyright 2013-2017 the original author or authors.
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

import java.util.Arrays;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.http.server.reactive.ServerHttpRequest;

import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_ALREADY_PREFIXED_ATTR;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_REQUEST_URL_ATTR;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.addOriginalRequestUrl;

/**
 * @author Spencer Gibb
 */
public class PrefixPathGatewayFilterFactory extends AbstractGatewayFilterFactory<PrefixPathGatewayFilterFactory.Config> {

	private static final Log log = LogFactory.getLog(PrefixPathGatewayFilterFactory.class);

	public static final String PREFIX_KEY = "prefix";

	public PrefixPathGatewayFilterFactory() {
		super(Config.class);
	}

	@Override
	public List<String> shortcutFieldOrder() {
		return Arrays.asList(PREFIX_KEY);
	}

	@Override
	public GatewayFilter apply(Config config) {
		return (exchange, chain) -> {

			boolean alreadyPrefixed = exchange.getAttributeOrDefault(GATEWAY_ALREADY_PREFIXED_ATTR, false);
			if (alreadyPrefixed) {
				return chain.filter(exchange);
			}
			exchange.getAttributes().put(GATEWAY_ALREADY_PREFIXED_ATTR, true);

			ServerHttpRequest req = exchange.getRequest();
			addOriginalRequestUrl(exchange, req.getURI());
			String newPath = config.prefix + req.getURI().getRawPath();

			ServerHttpRequest request = req.mutate()
					.path(newPath)
					.build();

			exchange.getAttributes().put(GATEWAY_REQUEST_URL_ATTR, request.getURI());

			if (log.isTraceEnabled()) {
				log.trace("Prefixed URI with: "+config.prefix+" -> "+request.getURI());
			}

			return chain.filter(exchange.mutate().request(request).build());
		};
	}

	public static class Config {
		private String prefix;

		public String getPrefix() {
			return prefix;
		}

		public void setPrefix(String prefix) {
			this.prefix = prefix;
		}
	}
}
