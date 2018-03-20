/*
 * Copyright 2017-2018 the original author or authors.
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

package org.springframework.cloud.gateway.filter.headers;

import org.springframework.http.HttpHeaders;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;

import java.util.List;

public interface HttpHeadersFilter {

	/**
	 * Filters a set of Http Headers
	 * 
	 * @param input Http Headers
	 * @param exchange
	 * @return filtered Http Headers
	 */
	HttpHeaders filter(HttpHeaders input, ServerWebExchange exchange);

	static HttpHeaders filter(List<HttpHeadersFilter> filters, HttpHeaders input,
			ServerWebExchange exchange) {
		return filter(filters, input, exchange, DownStreamPath.REQUEST);
	}

	
	static HttpHeaders filter(List<HttpHeadersFilter> filters,
			HttpHeaders input, ServerWebExchange exchange, DownStreamPath downStreamPath) {
		HttpHeaders response = input;
		if (filters != null) {
			return Flux.fromIterable(filters)
					.filter(httpFilter -> httpFilter.supports(downStreamPath))
					.reduce(input, (headers, filter) -> filter.filter(headers, exchange)).block();

		}
		return response;
	}

	default boolean supports(DownStreamPath downStreamPath) {
		if (downStreamPath.equals(DownStreamPath.REQUEST)) return true;
		
		return false;
	}
}
