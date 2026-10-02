/*
 * Copyright © 2013 Broadcom Inc. and/or its subsidiaries. All Rights Reserved.
 * Copyright 2013-present the original author or authors.
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
