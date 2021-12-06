/*
 * Copyright 2013-2020 the original author or authors.
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

import reactor.netty.http.server.HttpServerResponse;

import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.AbstractServerHttpResponse;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;

/**
 * @author Alberto C. Ríos
 */
public class GRPCResponseHeadersFilter implements HttpHeadersFilter, Ordered {

	@Override
	public HttpHeaders filter(HttpHeaders headers, ServerWebExchange exchange) {
		ServerHttpResponse response = exchange.getResponse();
		HttpHeaders responseHeaders = response.getHeaders();
		if (isGRPC(exchange)) {
			String trailerHeaderValue = "grpc-status";
			String originalTrailerHeaderValue = responseHeaders.getFirst(HttpHeaders.TRAILER);
			if (originalTrailerHeaderValue != null) {
				trailerHeaderValue += "," + originalTrailerHeaderValue;
			}
			responseHeaders.set(HttpHeaders.TRAILER, trailerHeaderValue);
			((HttpServerResponse) ((AbstractServerHttpResponse) response).getNativeResponse())
					.trailerHeaders(h -> h.set("grpc-status", "0"));
		}
		return headers;
	}

	private boolean isGRPC(ServerWebExchange exchange) {
		String contentTypeValue = exchange.getRequest().getHeaders().getFirst(HttpHeaders.CONTENT_TYPE);
		return StringUtils.startsWithIgnoreCase(contentTypeValue, "application/grpc");
	}

	@Override
	public boolean supports(Type type) {
		return Type.RESPONSE.equals(type);
	}

	@Override
	public int getOrder() {
		return 0;
	}

}
