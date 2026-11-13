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

package org.springframework.cloud.gateway.filter.factory.cache.keygenerator;

import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.server.ServerWebExchange;

/**
 * It generates key value based on the id of the route matched by the exchange, so cached
 * responses are not shared between routes.
 */
public class RouteIdKeyValueGenerator implements KeyValueGenerator {

	@Override
	public String getKeyValue(ServerHttpRequest request) {
		// a request alone carries no route information
		return "";
	}

	@Override
	public String getKeyValue(ServerWebExchange exchange) {
		Route route = exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR);
		return route != null ? route.getId() : "";
	}

}
