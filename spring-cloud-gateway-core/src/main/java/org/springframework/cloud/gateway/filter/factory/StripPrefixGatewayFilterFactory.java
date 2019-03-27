/*
 * Copyright 2013-2018 the original author or authors.
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
import java.util.stream.Collectors;

import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.util.StringUtils;

import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_REQUEST_URL_ATTR;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.addOriginalRequestUrl;

/**
 * This filter removes the first part of the path, known as the prefix, from the request
 * before sending it downstream
 * @author Ryan Baxter
 */
public class StripPrefixGatewayFilterFactory extends AbstractGatewayFilterFactory<StripPrefixGatewayFilterFactory.Config> {

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
		return (exchange, chain) ->  {
			ServerHttpRequest request = exchange.getRequest();
			addOriginalRequestUrl(exchange, request.getURI());
			String path = request.getURI().getRawPath();
			String newPath = "/" + Arrays.stream(StringUtils.tokenizeToStringArray(path, "/"))
					.skip(config.parts).collect(Collectors.joining("/"));
			newPath += (newPath.length() > 1 && path.endsWith("/") ? "/" : "");
			ServerHttpRequest newRequest = request.mutate()
					.path(newPath)
					.build();

			exchange.getAttributes().put(GATEWAY_REQUEST_URL_ATTR, newRequest.getURI());

			return chain.filter(exchange.mutate().request(newRequest).build());
		};
	}

	public static class Config {
		private int parts;

		public int getParts() {
			return parts;
		}

		public void setParts(int parts) {
			this.parts = parts;
		}
	}

}
