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

import java.net.URI;

import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.util.StringUtils;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * @author Spencer Gibb
 */
public class AddRequestParameterGatewayFilterFactory extends AbstractNameValueGatewayFilterFactory {

	@Override
	public GatewayFilter apply(NameValueConfig config) {
		return (exchange, chain) -> {
			URI uri = exchange.getRequest().getURI();
			StringBuilder query = new StringBuilder();
			String originalQuery = uri.getRawQuery();

			if (StringUtils.hasText(originalQuery)) {
				query.append(originalQuery);
				if (originalQuery.charAt(originalQuery.length() - 1) != '&') {
					query.append('&');
				}
			}

			//TODO urlencode?
			query.append(config.getName());
			query.append('=');
			query.append(config.getValue());

			try {
				URI newUri = UriComponentsBuilder.fromUri(uri)
						.replaceQuery(query.toString())
						.build(true)
						.toUri();

				ServerHttpRequest request = exchange.getRequest().mutate().uri(newUri).build();

				return chain.filter(exchange.mutate().request(request).build());
			} catch (RuntimeException ex) {
				throw new IllegalStateException("Invalid URI query: \"" + query.toString() + "\"");
			}
		};
	}

}
