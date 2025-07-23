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
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;

import static org.springframework.cloud.gateway.support.GatewayToStringStyler.filterToStringCreator;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_REQUEST_URL_ATTR;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.addOriginalRequestUrl;

/**
 * This filter removes the first part of the path, known as the prefix, from the request
 * before sending it downstream.
 *
 * @author Ryan Baxter
 */
public class StripPrefixGatewayFilterFactory
		extends AbstractGatewayFilterFactory<StripPrefixGatewayFilterFactory.Config> {

	/**
	 * Parts key.
	 */
	public static final String PARTS_KEY = "parts";

	public StripPrefixGatewayFilterFactory() {
		super(Config.class);
	}

	@Override
	public List<String> shortcutFieldOrder() {
		return Arrays.asList(PARTS_KEY);
	}

	@Override
	public GatewayFilter apply(Config config) {
		return new GatewayFilter() {
			@Override
			public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
				ServerHttpRequest request = exchange.getRequest();
				addOriginalRequestUrl(exchange, request.getURI());
				String path = request.getURI().getRawPath();
				String[] originalParts = StringUtils.tokenizeToStringArray(path, "/");

				// all new paths start with /
				StringBuilder newPath = new StringBuilder("/");
				for (int i = 0; i < originalParts.length; i++) {
					if (i >= config.getParts()) {
						// only append slash if this is the second part or greater
						if (newPath.length() > 1) {
							newPath.append('/');
						}
						newPath.append(originalParts[i]);
					}
				}
				if (newPath.length() > 1 && path.endsWith("/")) {
					newPath.append('/');
				}

				ServerHttpRequest newRequest = request.mutate().path(newPath.toString()).build();

				exchange.getAttributes().put(GATEWAY_REQUEST_URL_ATTR, newRequest.getURI());

				return chain.filter(exchange.mutate().request(newRequest).build());
			}

			@Override
			public String toString() {
				return filterToStringCreator(StripPrefixGatewayFilterFactory.this).append("parts", config.getParts())
					.toString();
			}
		};
	}

	public static class Config {

		private int parts = 1;

		public int getParts() {
			return parts;
		}

		public void setParts(int parts) {
			this.parts = parts;
		}

	}

}
