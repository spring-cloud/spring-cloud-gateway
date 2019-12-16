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

package org.springframework.cloud.gateway.filter;

import java.net.URI;

import reactor.core.publisher.Mono;

import org.springframework.core.Ordered;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;

/**
 * This filter ensures the path added to the URI of the route is "clean".
 *
 * For example if the path was <code>/foo/../bar</code>, the path after running through
 * this filter would be <code>/bar</code>
 *
 * @author Ryan Baxter
 */
public class CleanPathFilter implements GlobalFilter, Ordered {

	@Override
	public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
		URI uri = exchange.getRequest().getURI();
		if (StringUtils.isEmpty(uri.getRawPath())) {
			return chain.filter(exchange);
		}
		exchange = exchange.mutate().request(exchange.getRequest().mutate()
				.path(StringUtils.cleanPath(uri.getRawPath())).build()).build();
		return chain.filter(exchange);
	}

	@Override
	public int getOrder() {
		return 0;
	}

}
