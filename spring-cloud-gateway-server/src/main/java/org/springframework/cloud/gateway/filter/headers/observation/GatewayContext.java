/*
 * Copyright 2013-2022 the original author or authors.
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

package org.springframework.cloud.gateway.filter.headers.observation;

import java.util.Collections;
import java.util.Objects;

import io.micrometer.observation.transport.RequestReplySenderContext;

import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.server.ServerWebExchange;

/**
 * A {@link RequestReplySenderContext} for {@link ServerHttpRequest} and
 * {@link ServerHttpResponse}.
 *
 * @author Marcin Grzejszczak
 * @since 4.0.0
 */
public class GatewayContext extends RequestReplySenderContext<HttpHeaders, ServerHttpResponse> {

	private final ServerHttpRequest request;

	private final ServerWebExchange serverWebExchange;

	public GatewayContext(HttpHeaders headers, ServerHttpRequest request, ServerWebExchange serverWebExchange) {
		super((carrier, key, value) -> Objects.requireNonNull(carrier).put(key, Collections.singletonList(value)));
		this.request = request;
		this.serverWebExchange = serverWebExchange;
		setCarrier(headers);
	}

	public ServerHttpRequest getRequest() {
		return this.request;
	}

	public ServerWebExchange getServerWebExchange() {
		return serverWebExchange;
	}

}
