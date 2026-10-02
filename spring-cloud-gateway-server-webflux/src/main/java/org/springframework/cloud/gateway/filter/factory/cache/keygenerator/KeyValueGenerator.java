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

import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.server.ServerWebExchange;

/**
 * Function to calculate a key value based on a {@link ServerHttpRequest}.
 *
 * @author Marta Medio
 * @author Ignacio Lozano
 */
interface KeyValueGenerator {

	/**
	 * Calls getKeyValue() and guards against null.
	 * @param request the request
	 * @return the key value, never null
	 * @deprecated in favor of {@link #apply(ServerWebExchange)}
	 */
	@Deprecated
	@SuppressWarnings("deprecation")
	default String apply(ServerHttpRequest request) {
		String key = getKeyValue(request);
		if (key == null) {
			return "";
		}
		return key;
	}

	/*
	 * Calls getKeyValue() and guards against null.
	 */
	default String apply(ServerWebExchange exchange) {
		String key = getKeyValue(exchange);
		if (key == null) {
			return "";
		}
		return key;
	}

	/**
	 * Calculates the key value for a request.
	 * @param request the request
	 * @return the key value
	 * @deprecated in favor of {@link #getKeyValue(ServerWebExchange)}
	 */
	@Deprecated
	String getKeyValue(ServerHttpRequest request);

	/*
	 * Defaults to the request based value. Override when the exchange is needed.
	 */
	@SuppressWarnings("deprecation")
	default String getKeyValue(ServerWebExchange exchange) {
		return getKeyValue(exchange.getRequest());
	}

}
