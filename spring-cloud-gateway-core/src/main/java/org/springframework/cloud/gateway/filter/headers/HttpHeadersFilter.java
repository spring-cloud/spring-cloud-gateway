/*
 * Copyright 2017-2018 the original author or authors.
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

package org.springframework.cloud.gateway.filter.headers;

import java.util.List;

import org.springframework.http.HttpHeaders;
import org.springframework.web.server.ServerWebExchange;

public interface HttpHeadersFilter {

	enum Type {
		REQUEST, RESPONSE
	}

	/**
	 * Filters a set of Http Headers
	 * 
	 * @param input Http Headers
	 * @param exchange
	 * @return filtered Http Headers
	 */
	HttpHeaders filter(HttpHeaders input, ServerWebExchange exchange);

	static HttpHeaders filterRequest(List<HttpHeadersFilter> filters,
							  ServerWebExchange exchange) {
		HttpHeaders headers = exchange.getRequest().getHeaders();
		return filter(filters, headers, exchange, Type.REQUEST);
	}

	static HttpHeaders filter(List<HttpHeadersFilter> filters, HttpHeaders input,
			ServerWebExchange exchange, Type type) {
		HttpHeaders response = input;
		if (filters != null) {
			HttpHeaders reduce = filters.stream()
					.filter(headersFilter -> headersFilter.supports(type))
					.reduce(input,
							(headers, filter) -> filter.filter(headers, exchange),
							(httpHeaders, httpHeaders2) -> {
								httpHeaders.addAll(httpHeaders2);
								return httpHeaders;
							});
			return reduce;
		}

		return response;
	}

	default boolean supports(Type type) {
		return type.equals(Type.REQUEST);
	}
}
