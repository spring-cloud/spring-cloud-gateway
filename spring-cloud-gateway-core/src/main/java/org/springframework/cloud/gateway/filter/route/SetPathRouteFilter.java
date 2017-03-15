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

package org.springframework.cloud.gateway.filter.route;

import java.net.URI;
import java.util.Map;

import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.server.WebFilter;
import org.springframework.web.util.UriTemplate;

import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.getAttribute;
import static org.springframework.web.reactive.function.server.RouterFunctions.URI_TEMPLATE_VARIABLES_ATTRIBUTE;

/**
 * @author Spencer Gibb
 */
public class SetPathRouteFilter implements RouteFilter {

	@Override
	@SuppressWarnings("unchecked")
	public WebFilter apply(String... args) {
		validate(1, args);
		UriTemplate uriTemplate = new UriTemplate(args[0]);

		return (exchange, chain) -> {
			Map<String, String> variables = getAttribute(exchange, URI_TEMPLATE_VARIABLES_ATTRIBUTE, Map.class);
			ServerHttpRequest req = exchange.getRequest();
			URI uri = uriTemplate.expand(variables);
			String newPath = uri.getPath();

			ServerHttpRequest request = req.mutate()
					.path(newPath)
					.build();

			return chain.filter(exchange.mutate().request(request).build());
		};
	}
}
