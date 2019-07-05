/*
 * Copyright 2013-2019 the original author or authors.
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

import java.util.List;

import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.http.server.reactive.ServerHttpRequest;

/**
 * @author Tony Clarke
 */
public class MapRequestHeaderGatewayFilterFactory extends AbstractNameValueGatewayFilterFactory {

	@Override
	public GatewayFilter apply(NameValueConfig config) {
		return (exchange, chain) -> {
			if (!exchange.getRequest().getHeaders().containsKey(config.getValue())) {
				return chain.filter(exchange);
			}
			List<String> headerValues = exchange.getRequest().getHeaders().get(config.getValue());

			ServerHttpRequest request = exchange.getRequest().mutate()
					.headers(i -> i.addAll(config.getName(), headerValues)).build();

			return chain.filter(exchange.mutate().request(request).build());
		};
	}

}
