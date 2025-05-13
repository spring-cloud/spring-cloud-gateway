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

import reactor.netty.http.client.HttpClientResponse;
import reactor.netty.http.server.HttpServerResponse;

import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.AbstractServerHttpResponse;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.http.server.reactive.ServerHttpResponseDecorator;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;

import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.CLIENT_RESPONSE_ATTR;

/**
 * @author Alberto C. Ríos
 */
public class GRPCResponseHeadersFilter implements HttpHeadersFilter, Ordered {

	private static final String GRPC_STATUS_HEADER = "grpc-status";

	private static final String GRPC_MESSAGE_HEADER = "grpc-message";

	@Override
	public HttpHeaders filter(HttpHeaders headers, ServerWebExchange exchange) {
		if (isGRPC(exchange)) {
			ServerHttpResponse response = exchange.getResponse();
			HttpHeaders responseHeaders = response.getHeaders();

			if (headers.containsKey(GRPC_STATUS_HEADER)) {
				if (!"0".equals(headers.getFirst(GRPC_STATUS_HEADER))) {
					response.setComplete(); // avoid empty DATA frame
				}
			}

			HttpClientResponse nettyInResponse = exchange.getAttribute(CLIENT_RESPONSE_ATTR);
			if (nettyInResponse != null) {
				nettyInResponse.trailerHeaders().subscribe(entries -> {
					if (entries.contains(GRPC_STATUS_HEADER)) {
						addTrailingHeader(entries, response, responseHeaders);
					}
				});
			}
		}

		return headers;
	}

	private void addTrailingHeader(io.netty.handler.codec.http.HttpHeaders sourceHeaders, ServerHttpResponse response,
			HttpHeaders responseHeaders) {
		String trailerHeaderValue = GRPC_STATUS_HEADER + "," + GRPC_MESSAGE_HEADER;
		String originalTrailerHeaderValue = responseHeaders.getFirst(HttpHeaders.TRAILER);
		if (originalTrailerHeaderValue != null) {
			trailerHeaderValue += "," + originalTrailerHeaderValue;
		}
		responseHeaders.set(HttpHeaders.TRAILER, trailerHeaderValue);

		HttpServerResponse nettyOutResponse = getNettyResponse(response);
		if (nettyOutResponse != null) {
			String grpcStatus = sourceHeaders.get(GRPC_STATUS_HEADER, "0");
			String grpcMessage = sourceHeaders.get(GRPC_MESSAGE_HEADER, "");
			nettyOutResponse.trailerHeaders(h -> {
				h.set(GRPC_STATUS_HEADER, grpcStatus);
				h.set(GRPC_MESSAGE_HEADER, grpcMessage);
			});
		}
	}

	private HttpServerResponse getNettyResponse(ServerHttpResponse response) {
		while (response instanceof ServerHttpResponseDecorator) {
			response = ((ServerHttpResponseDecorator) response).getDelegate();
		}
		if (response instanceof AbstractServerHttpResponse) {
			return ((AbstractServerHttpResponse) response).getNativeResponse();
		}
		return null;
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
