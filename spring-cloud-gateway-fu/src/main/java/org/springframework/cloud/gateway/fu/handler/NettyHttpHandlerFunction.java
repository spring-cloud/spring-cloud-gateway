/*
 * Copyright 2013-2018 the original author or authors.
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
package org.springframework.cloud.gateway.fu.handler;

import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.HttpMethod;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.core.io.buffer.NettyDataBuffer;
import org.springframework.core.io.buffer.NettyDataBufferFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.lang.Nullable;
import org.springframework.web.reactive.function.BodyExtractors;
import org.springframework.web.reactive.function.server.HandlerFunction;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.netty.NettyPipeline;
import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.ConnectionProvider;

import java.net.URI;
import java.util.Arrays;
import java.util.List;

class NettyHttpHandlerFunction implements HandlerFunction<ServerResponse> {
	private static final Log log = LogFactory.getLog(HandlerFunctions.class);

	private static final List<MediaType> streamingMediaTypes = Arrays
			.asList(MediaType.TEXT_EVENT_STREAM, MediaType.APPLICATION_STREAM_JSON);	private final URI uri;

	//TODO: use autoconfig of HttpClient
	HttpClient httpClient;

	public NettyHttpHandlerFunction(URI uri) {
		this.uri = uri;
		httpClient = HttpClient
				.create(ConnectionProvider.elastic("gateway-fu"));
	}

	@Override
	public Mono<ServerResponse> handle(ServerRequest request) {
		final HttpMethod method = HttpMethod.valueOf(request.method().toString());
		boolean encoded = containsEncodedQuery(uri);
		URI requestUrl = UriComponentsBuilder.fromUri(request.uri()).uri(uri)
				.build(encoded).toUri();
		final String url = requestUrl.toString();

		// HttpHeaders filtered = filterRequest(this.headersFilters.getIfAvailable(), exchange);

		final DefaultHttpHeaders httpHeaders = new DefaultHttpHeaders();
		// filtered.forEach(httpHeaders::set);
		request.headers().asHttpHeaders().forEach(httpHeaders::set);

		String transferEncoding = request.headers().asHttpHeaders().getFirst(HttpHeaders.TRANSFER_ENCODING);
		boolean chunkedTransfer = "chunked".equalsIgnoreCase(transferEncoding);

		boolean preserveHost = false; //exchange.getAttributeOrDefault(PRESERVE_HOST_HEADER_ATTRIBUTE, false);

		return httpClient.chunkedTransfer(chunkedTransfer)
				.request(method)
				.uri(url)
				.send((req, nettyOutbound) -> {
					req.headers(httpHeaders);

					if (preserveHost) {
						String host = request.headers().asHttpHeaders().getFirst(HttpHeaders.HOST);
						req.header(HttpHeaders.HOST, host);
					}

					return nettyOutbound
							.options(NettyPipeline.SendOptions::flushOnEach)
							.send(request.body(BodyExtractors.toDataBuffers()).map(dataBuffer ->
									((NettyDataBuffer) dataBuffer).getNativeBuffer()));
				}).responseConnection((res, connection) -> { //TODO: streaming?
					// }).responseSingle((res, connection) -> {
					return ServerResponse.status(res.status().code())
							.headers(responseHeaders -> res.responseHeaders().names()
									.forEach(name -> responseHeaders.addAll(name,
											res.responseHeaders().getAll(name))))
							.body((outputMessage, context) -> {
								NettyDataBufferFactory factory = (NettyDataBufferFactory) outputMessage
										.bufferFactory();
								Flux<NettyDataBuffer> body = connection.inbound().receive()
										.retain() //TODO: needed?
										.map(factory::wrap);
								MediaType contentType = null;
								try {
									contentType = MediaType.parseMediaType(res.responseHeaders().get(HttpHeaders.CONTENT_TYPE));
								} catch (Exception e) {
									log.trace("invalid media type", e);
								}
								return (isStreamingMediaType(contentType)
										? outputMessage.writeAndFlushWith(body.map(Flux::just))
										: outputMessage.writeWith(body));
							});

					// TODO: filter response headers
					/*String contentTypeValue = headers.getFirst(HttpHeaders.CONTENT_TYPE);
					if (StringUtils.hasLength(contentTypeValue)) {
						exchange.getAttributes().put(ORIGINAL_RESPONSE_CONTENT_TYPE_ATTR, contentTypeValue);
					}

					HttpHeaders filteredResponseHeaders = HttpHeadersFilter.filter(
							this.headersFilters.getIfAvailable(), headers, exchange, Type.RESPONSE);

					response.getHeaders().putAll(filteredResponseHeaders);*/

					// Defer committing the response until all route filters have run
					// Put client response as ServerWebExchange attribute and write response later NettyWriteResponseFilter
					// exchange.getAttributes().put(CLIENT_RESPONSE_ATTR, res);
					// exchange.getAttributes().put(CLIENT_RESPONSE_CONN_ATTR, connection);

				}).singleOrEmpty();
	}


	private static boolean isStreamingMediaType(@Nullable MediaType contentType) {
		return (contentType != null && streamingMediaTypes.stream()
				.anyMatch(contentType::isCompatibleWith));
	}

	public static boolean containsEncodedQuery(URI uri) {
		if (uri.getRawQuery() == null) {
			return false;
		}
		return uri.getRawQuery().contains("%");
	}
}
