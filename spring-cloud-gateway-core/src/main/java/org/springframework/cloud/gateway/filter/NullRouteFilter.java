/*
 * Copyright 2013-2018 the original author or authors.
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
 */

package org.springframework.cloud.gateway.filter;

import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_REQUEST_URL_ATTR;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.isAlreadyRouted;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.setAlreadyRouted;

import java.net.URI;
import java.util.Optional;

import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ServerWebExchange;

import reactor.core.publisher.Mono;

/**
 * NullRouteFilter is used to return a status code in the response without any backend.
 *
 * @author Jean-Philippe Plante
 */
public class NullRouteFilter implements GlobalFilter, Ordered {

	@Override
	public int getOrder() {
		return Ordered.LOWEST_PRECEDENCE;
	}

	@Override
	public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
		URI requestUrl = exchange.getRequiredAttribute(GATEWAY_REQUEST_URL_ATTR);

		// check for nullroute scheme
		String scheme = requestUrl.getScheme();
		if (isAlreadyRouted(exchange) || (!"nullroute".equals(scheme))) {
			return chain.filter(exchange);
		}
		setAlreadyRouted(exchange);

		// status code is in the host part of the uri. If not found, will use 200 (OK)
		Optional<HttpStatus> httpStatus = Optional.empty();
		if (requestUrl.getHost().matches("\\d+")) {
			Integer httpStatusCode = Integer.valueOf(requestUrl.getHost());
			httpStatus = Optional.ofNullable(HttpStatus.resolve(httpStatusCode));
		}

		exchange.getResponse().setStatusCode(httpStatus.orElse(HttpStatus.OK));

		return chain.filter(exchange);
	}

}
