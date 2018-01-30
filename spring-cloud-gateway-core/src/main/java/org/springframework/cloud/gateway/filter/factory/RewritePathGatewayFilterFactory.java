/*
 * Copyright 2013-2017 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
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

import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.tuple.Tuple;

import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_REQUEST_URL_ATTR;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.addOriginalRequestUrl;

/**
 * @author Spencer Gibb
 */
public class RewritePathGatewayFilterFactory implements GatewayFilterFactory {

	public static final String REGEXP_KEY = "regexp";
	public static final String REPLACEMENT_KEY = "replacement";

	@Override
	public List<String> argNames() {
		return Arrays.asList(REGEXP_KEY, REPLACEMENT_KEY);
	}

	@Override
	public GatewayFilter apply(Tuple args) {
		final String regex = args.getString(REGEXP_KEY);
		String replacement = args.getString(REPLACEMENT_KEY).replace("$\\", "$");
		return apply(regex, replacement);
	}

	public GatewayFilter apply(String regex, String replacement) {
		return (exchange, chain) -> {
			ServerHttpRequest req = exchange.getRequest();
			addOriginalRequestUrl(exchange, req.getURI());
			String path = req.getURI().getPath();
			String newPath = path.replaceAll(regex, replacement);

			ServerHttpRequest request = mutate(req)
					.path(newPath)
					.build();

			exchange.getAttributes().put(GATEWAY_REQUEST_URL_ATTR, request.getURI());

			return chain.filter(exchange.mutate().request(request).build());
		};
	}
}
