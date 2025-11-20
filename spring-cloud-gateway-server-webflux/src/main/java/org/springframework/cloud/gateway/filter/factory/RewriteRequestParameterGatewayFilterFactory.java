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

import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import org.jspecify.annotations.Nullable;
import reactor.core.publisher.Mono;

import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.util.UriComponentsBuilder;

import static org.springframework.cloud.gateway.support.GatewayToStringStyler.filterToStringCreator;
import static org.springframework.util.CollectionUtils.unmodifiableMultiValueMap;

/**
 * @author Fredrich Ombico
 */
public class RewriteRequestParameterGatewayFilterFactory
		extends AbstractGatewayFilterFactory<RewriteRequestParameterGatewayFilterFactory.Config> {

	/**
	 * Replacement key.
	 */
	public static final String REPLACEMENT_KEY = "replacement";

	public RewriteRequestParameterGatewayFilterFactory() {
		super(Config.class);
	}

	@Override
	public List<String> shortcutFieldOrder() {
		return Arrays.asList(NAME_KEY, REPLACEMENT_KEY);
	}

	@Override
	public GatewayFilter apply(Config config) {
		return new GatewayFilter() {
			@Override
			public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
				String name = Objects.requireNonNull(config.getName(), "name must not be null");
				String replacement = Objects.requireNonNull(config.getReplacement(), "replacement must not be null");

				ServerHttpRequest req = exchange.getRequest();

				UriComponentsBuilder uriComponentsBuilder = UriComponentsBuilder.fromUri(req.getURI());

				MultiValueMap<String, String> queryParams = new LinkedMultiValueMap<>(req.getQueryParams());
				if (queryParams.containsKey(name)) {
					queryParams.remove(name);
					queryParams.add(name, replacement);
				}

				try {
					MultiValueMap<String, String> encodedQueryParams = ServerWebExchangeUtils
						.encodeQueryParams(queryParams);
					URI uri = uriComponentsBuilder.replaceQueryParams(unmodifiableMultiValueMap(encodedQueryParams))
						.build(true)
						.toUri();

					ServerHttpRequest request = req.mutate().uri(uri).build();
					return chain.filter(exchange.mutate().request(request).build());
				}
				catch (IllegalArgumentException ex) {
					throw new IllegalStateException("Invalid URI query: \"" + queryParams + "\"");
				}
			}

			@Override
			public String toString() {
				String name = Objects.requireNonNull(config.getName(), "name must not be null");
				String replacement = Objects.requireNonNull(config.getReplacement(), "replacement must not be null");
				return filterToStringCreator(RewriteRequestParameterGatewayFilterFactory.this).append(name, replacement)
					.toString();
			}
		};
	}

	public static class Config extends AbstractGatewayFilterFactory.NameConfig {

		private @Nullable String replacement;

		public @Nullable String getReplacement() {
			return replacement;
		}

		public Config setReplacement(String replacement) {
			this.replacement = replacement;
			return this;
		}

	}

}
