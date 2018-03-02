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
 *
 */

package org.springframework.cloud.gateway.fn;

import java.net.URI;
import java.util.Arrays;
import java.util.List;

import org.springframework.core.io.buffer.NettyDataBuffer;
import org.springframework.core.io.buffer.NettyDataBufferFactory;
import org.springframework.http.MediaType;
import org.springframework.lang.Nullable;
import org.springframework.web.reactive.function.server.HandlerFunction;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.util.UriComponentsBuilder;

import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpMethod;
import reactor.core.publisher.Flux;
import reactor.ipc.netty.NettyPipeline;
import reactor.ipc.netty.http.client.HttpClient;
import reactor.ipc.netty.http.client.HttpClientRequest;
import reactor.ipc.netty.resources.PoolResources;

public abstract class HandlerFunctions {
	private static final List<MediaType> streamingMediaTypes = Arrays.asList(MediaType.TEXT_EVENT_STREAM,
			MediaType.APPLICATION_STREAM_JSON);

	public static HandlerFunction<ServerResponse> http(String uri) {
		return http(URI.create(uri));
	}

    public static HandlerFunction<ServerResponse> http(URI uri) {
		HttpClient httpClient = HttpClient.create(opts ->
				opts.poolResources(PoolResources.elastic("proxy")));

		return request -> {
			// TODO: filter?
			boolean encoded = containsEncodedQuery(uri);
			URI requestUrl = UriComponentsBuilder.fromUri(request.uri()).uri(uri)
					.build(encoded)
					.toUri();

			final DefaultHttpHeaders httpHeaders = new DefaultHttpHeaders();
			request.headers().asHttpHeaders().forEach(httpHeaders::set);
			HttpMethod method = HttpMethod.valueOf(request.methodName());

			return httpClient
					.request(method, requestUrl.toString(), req -> {
						HttpClientRequest proxyRequest = req
								.options(NettyPipeline.SendOptions::flushOnEach)
								.headers(httpHeaders).failOnServerError(false)
								.failOnClientError(false);

						Flux<ByteBuf> body = request
								.body((inputMessage, context) -> inputMessage.getBody())
								.map(NettyDataBufferFactory::toByteBuf);
						return proxyRequest.sendHeaders().send(body);
					}).flatMap(res -> ServerResponse.status(res.status().code())
							.headers(responseHeaders -> res.responseHeaders().names().forEach(name ->
									responseHeaders.addAll(name, res.responseHeaders().getAll(name))))
							.body((outputMessage, context) -> {
								NettyDataBufferFactory factory = (NettyDataBufferFactory) outputMessage
										.bufferFactory();
								Flux<NettyDataBuffer> body = res.receive().retain() // needed?
										.map(factory::wrap);
								String contentType = res.responseHeaders().get(HttpHeaderNames.CONTENT_TYPE);
								return (isStreamingMediaType(contentType) ?
										outputMessage.writeAndFlushWith(body.map(Flux::just)) : outputMessage.writeWith(body));
							}));
		};
	}

	private static boolean isStreamingMediaType(@Nullable String contentType) {
		if (contentType == null) {
			return false;
		}
		MediaType mediaType = MediaType.valueOf(contentType);
		return (mediaType != null && streamingMediaTypes.stream()
				.anyMatch(mediaType::isCompatibleWith));
	}

	public static boolean containsEncodedQuery(URI uri) {
		if (uri.getRawQuery() == null) {
			return false;
		}
		return uri.getRawQuery().contains("%");
	}
}
