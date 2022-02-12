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

import java.net.URI;
import java.util.Arrays;
import java.util.List;

import reactor.core.publisher.Mono;

import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.util.UriComponentsBuilder;

import static org.springframework.cloud.gateway.support.GatewayToStringStyler.filterToStringCreator;
import static org.springframework.util.CollectionUtils.unmodifiableMultiValueMap;

/**
 * @author Thirunavukkarasu Ravichandran
 */
public class RemoveRequestParameterGatewayFilterFactory
		extends AbstractGatewayFilterFactory<AbstractGatewayFilterFactory.NameConfig> {

	public RemoveRequestParameterGatewayFilterFactory() {
		super(NameConfig.class);
	}

	@Override
	public List<String> shortcutFieldOrder() {
		return Arrays.asList(NAME_KEY);
	}

	@Override
	public GatewayFilter apply(NameConfig config) {
		return new GatewayFilter() {
			@Override
			public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
				ServerHttpRequest request = exchange.getRequest();
				MultiValueMap<String, String> queryParams = new LinkedMultiValueMap<>(request.getQueryParams());
				queryParams.remove(config.getName());

				URI newUri = UriComponentsBuilder.fromUri(request.getURI())
						.replaceQueryParams(unmodifiableMultiValueMap(queryParams)).build().toUri();

				ServerHttpRequest updatedRequest = exchange.getRequest().mutate().uri(newUri).build();

				return chain.filter(exchange.mutate().request(updatedRequest).build());
			}

			@Override
			public String toString() {
				return filterToStringCreator(RemoveRequestParameterGatewayFilterFactory.this)
						.append("name", config.getName()).toString();
			}
		};
	}

}
