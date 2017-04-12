/*
 * Copyright 2013-2017 the original author or authors.
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

package org.springframework.cloud.gateway.handler;

import java.net.URI;
import java.util.Optional;

import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebHandler;

import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.CLIENT_RESPONSE_ATTR;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_REQUEST_URL_ATTR;

import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.HttpMethod;
import reactor.core.publisher.Mono;
import reactor.ipc.netty.NettyPipeline;
import reactor.ipc.netty.http.client.HttpClient;

/**
 * @author Spencer Gibb
 */
public class NettyProxyWebHandler implements WebHandler {

	private final HttpClient httpClient;

	public NettyProxyWebHandler(HttpClient httpClient) {
		this.httpClient = httpClient;
	}

	@Override
	public Mono<Void> handle(ServerWebExchange exchange) {
		Optional<URI> requestUrl = exchange.getAttribute(GATEWAY_REQUEST_URL_ATTR);
		ServerHttpRequest request = exchange.getRequest();

		final HttpMethod method = HttpMethod.valueOf(request.getMethod().toString());
		final String url = requestUrl.get().toString();

		final DefaultHttpHeaders httpHeaders = new DefaultHttpHeaders();
		request.getHeaders().forEach(httpHeaders::set);

		return this.httpClient.request(method, url, req ->
				req.options(NettyPipeline.SendOptions::flushOnEach)
						.headers(httpHeaders)
						.sendHeaders()
						.send(request.getBody()
								.map(DataBuffer::asByteBuffer)
								.map(Unpooled::wrappedBuffer)))
				.flatMap(res -> {
					// Defer committing the response until all route filters have run
					// Put client response as ServerWebExchange attribute and write response later WriteResponseFilter
					exchange.getAttributes().put(CLIENT_RESPONSE_ATTR, res);

					ServerHttpResponse response = exchange.getResponse();
					// put headers and status so filters can modify the response
					final HttpHeaders headers = new HttpHeaders();
					res.responseHeaders().forEach(entry -> headers.add(entry.getKey(), entry.getValue()));

					response.getHeaders().putAll(headers);
					response.setStatusCode(HttpStatus.valueOf(res.status().code()));

					return Mono.empty();
				});
	}
}
