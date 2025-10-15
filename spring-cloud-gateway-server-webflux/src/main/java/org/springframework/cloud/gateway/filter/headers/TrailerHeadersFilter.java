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

package org.springframework.cloud.gateway.filter.headers;

import java.util.List;

import org.jspecify.annotations.Nullable;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClientResponse;
import reactor.netty.http.server.HttpServerResponse;

import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.AbstractServerHttpResponse;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.http.server.reactive.ServerHttpResponseDecorator;
import org.springframework.web.server.ServerWebExchange;

public interface TrailerHeadersFilter {

	static Mono<HttpHeaders> filter(@Nullable List<HttpHeadersFilter> filters, ServerWebExchange exchange,
			@Nullable HttpClientResponse response) {
		if (response == null) {
			return Mono.empty();
		}
		Mono<HttpHeaders> headers = response.trailerHeaders().map(input -> {
			HttpHeaders httpHeaders = new HttpHeaders();
			input.forEach(entry -> httpHeaders.add(entry.getKey(), entry.getValue()));
			return httpHeaders;
		});
		return filter(filters, headers, exchange);
	}

	private static Mono<HttpHeaders> filter(@Nullable List<HttpHeadersFilter> filters, Mono<HttpHeaders> input,
			ServerWebExchange exchange) {

		Mono<HttpHeaders> filtered = input;
		if (filters != null) {
			for (int i = 0; i < filters.size(); i++) {
				if (filters.get(i) instanceof TrailerHeadersFilter filter) {
					filtered = filtered.map(headers -> filter.trailers(headers, exchange));
				}
			}
		}

		return filtered.doOnSuccess(headers -> {

			if (headers == null || headers.isEmpty()) {
				return;
			}

			ServerHttpResponse response = exchange.getResponse();
			while (response instanceof ServerHttpResponseDecorator) {
				response = ((ServerHttpResponseDecorator) response).getDelegate();
			}
			if (response instanceof AbstractServerHttpResponse) {
				((HttpServerResponse) ((AbstractServerHttpResponse) response).getNativeResponse()).trailerHeaders(h -> {
					headers.forEach((key, values) -> {
						for (String value : values) {
							h.add(key, value);
						}
					});
				});
			}

		});
	}

	/**
	 * Filters a set of Http Headers.
	 * @param input Http Headers
	 * @param exchange a {@link ServerWebExchange} that should be filtered
	 * @return filtered Http Headers
	 */
	HttpHeaders trailers(@Nullable HttpHeaders input, ServerWebExchange exchange);

}
