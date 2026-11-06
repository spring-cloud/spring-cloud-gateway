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
import java.util.Objects;

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
 * @author Spencer Gibb
 */
public class AddRequestParameterGatewayFilterFactory extends AbstractNameValueGatewayFilterFactory {

	@Override
	public GatewayFilter apply(NameValueConfig config) {
		return new GatewayFilter() {
			@Override
			public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
				String name = Objects.requireNonNull(config.getName(), "name must not be null");
				String rawValue = Objects.requireNonNull(config.getValue(), "value must not be null");
				ServerHttpRequest request = exchange.getRequest();
				MultiValueMap<String, String> queryParams = new LinkedMultiValueMap<>();
				queryParams.addAll(request.getQueryParams());
				String value = ServerWebExchangeUtils.expand(exchange, rawValue);
				queryParams.add(name, value);

				try {
					MultiValueMap<String, String> encodedQueryParams = ServerWebExchangeUtils
						.encodeQueryParams(queryParams);
					URI newUri = UriComponentsBuilder.fromUri(request.getURI())
						.replaceQueryParams(unmodifiableMultiValueMap(encodedQueryParams))
						.build(true)
						.toUri();

					ServerHttpRequest updatedRequest = request.mutate().uri(newUri).build();

					return chain.filter(exchange.mutate().request(updatedRequest).build());
				}
				catch (IllegalArgumentException ex) {
					throw new IllegalStateException("Invalid URI query: \"" + queryParams + "\"", ex);
				}
			}

			@Override
			public String toString() {
				String name = config.getName();
				String value = config.getValue();
				return filterToStringCreator(AddRequestParameterGatewayFilterFactory.this)
					.append(name != null ? name : "", value != null ? value : "")
					.toString();
			}
		};
	}

}
