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

import org.jspecify.annotations.Nullable;
import reactor.netty.http.server.HttpServerResponse;

import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.AbstractServerHttpResponse;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.http.server.reactive.ServerHttpResponseDecorator;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;

/**
 * @author Alberto C. Ríos
 */
public class GRPCResponseHeadersFilter implements HttpHeadersFilter, Ordered {

	private static final String GRPC_STATUS_HEADER = "grpc-status";

	private static final String GRPC_MESSAGE_HEADER = "grpc-message";

	private static final String GRPC_STATUS_DETAILS_BIN_HEADER = "grpc-status-details-bin";

	@Override
	public HttpHeaders filter(HttpHeaders headers, ServerWebExchange exchange) {
		ServerHttpResponse response = exchange.getResponse();
		if (isGRPC(exchange)) {
			String grpcStatus = getGrpcStatus(headers);
			String grpcMessage = getGrpcMessage(headers);
			String grpcStatusDetailsBin = getGrpcStatusDetailsBin(headers);
			if (grpcStatus != null) {
				while (response instanceof ServerHttpResponseDecorator) {
					response = ((ServerHttpResponseDecorator) response).getDelegate();
				}
				if (response instanceof AbstractServerHttpResponse) {
					((HttpServerResponse) ((AbstractServerHttpResponse) response).getNativeResponse())
						.trailerHeaders(h -> {
							h.set(GRPC_STATUS_HEADER, grpcStatus);
							h.set(GRPC_MESSAGE_HEADER, grpcMessage);
							if (grpcStatusDetailsBin != null) {
								h.set(GRPC_STATUS_DETAILS_BIN_HEADER, grpcStatusDetailsBin);
							}
						});
				}
			}

		}
		return headers;
	}

	private boolean isGRPC(ServerWebExchange exchange) {
		String contentTypeValue = exchange.getRequest().getHeaders().getFirst(HttpHeaders.CONTENT_TYPE);
		return StringUtils.startsWithIgnoreCase(contentTypeValue, "application/grpc");
	}

	private @Nullable String getGrpcStatus(HttpHeaders headers) {
		final String grpcStatusValue = headers.getFirst(GRPC_STATUS_HEADER);
		return StringUtils.hasText(grpcStatusValue) ? grpcStatusValue : null;
	}

	private @Nullable String getGrpcStatusDetailsBin(HttpHeaders headers) {
		final String grpcStatusDetailsBinValue = headers.getFirst(GRPC_STATUS_DETAILS_BIN_HEADER);
		return StringUtils.hasText(grpcStatusDetailsBinValue) ? grpcStatusDetailsBinValue : null;
	}

	private String getGrpcMessage(HttpHeaders headers) {
		final String grpcStatusValue = headers.getFirst(GRPC_MESSAGE_HEADER);
		return StringUtils.hasText(grpcStatusValue) ? grpcStatusValue : "";
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
