/*
 * Copyright 2013-2019 the original author or authors.
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

package org.springframework.cloud.gateway.filter.factory.rewrite;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.NettyWriteResponseFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.cloud.gateway.filter.factory.GatewayFilterFactory;
import org.springframework.cloud.gateway.support.BodyInserterContext;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.codec.HttpMessageReader;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.http.server.reactive.ServerHttpResponseDecorator;
import org.springframework.web.reactive.function.BodyInserter;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.server.ServerWebExchange;

import static java.util.function.Function.identity;
import static org.springframework.cloud.gateway.support.GatewayToStringStyler.filterToStringCreator;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.ORIGINAL_RESPONSE_CONTENT_TYPE_ATTR;

/**
 * GatewayFilter that modifies the response body.
 */
public class ModifyResponseBodyGatewayFilterFactory extends
		AbstractGatewayFilterFactory<ModifyResponseBodyGatewayFilterFactory.Config> {

	private final Map<String, MessageBodyDecoder> messageBodyDecoders;

	private final Map<String, MessageBodyEncoder> messageBodyEncoders;

	private final List<HttpMessageReader<?>> messageReaders;

	public ModifyResponseBodyGatewayFilterFactory(
			List<HttpMessageReader<?>> messageReaders,
			Set<MessageBodyDecoder> messageBodyDecoders,
			Set<MessageBodyEncoder> messageBodyEncoders) {
		super(Config.class);
		this.messageReaders = messageReaders;
		this.messageBodyDecoders = messageBodyDecoders.stream()
				.collect(Collectors.toMap(MessageBodyDecoder::encodingType, identity()));
		this.messageBodyEncoders = messageBodyEncoders.stream()
				.collect(Collectors.toMap(MessageBodyEncoder::encodingType, identity()));
	}

	@Override
	public GatewayFilter apply(Config config) {
		ModifyResponseGatewayFilter gatewayFilter = new ModifyResponseGatewayFilter(
				config);
		gatewayFilter.setFactory(this);
		return gatewayFilter;
	}

	public static class Config {

		private Class inClass;

		private Class outClass;

		private Map<String, Object> inHints;

		private Map<String, Object> outHints;

		private String newContentType;

		private RewriteFunction rewriteFunction;

		public Class getInClass() {
			return inClass;
		}

		public Config setInClass(Class inClass) {
			this.inClass = inClass;
			return this;
		}

		public Class getOutClass() {
			return outClass;
		}

		public Config setOutClass(Class outClass) {
			this.outClass = outClass;
			return this;
		}

		public Map<String, Object> getInHints() {
			return inHints;
		}

		public Config setInHints(Map<String, Object> inHints) {
			this.inHints = inHints;
			return this;
		}

		public Map<String, Object> getOutHints() {
			return outHints;
		}

		public Config setOutHints(Map<String, Object> outHints) {
			this.outHints = outHints;
			return this;
		}

		public String getNewContentType() {
			return newContentType;
		}

		public Config setNewContentType(String newContentType) {
			this.newContentType = newContentType;
			return this;
		}

		public RewriteFunction getRewriteFunction() {
			return rewriteFunction;
		}

		public Config setRewriteFunction(RewriteFunction rewriteFunction) {
			this.rewriteFunction = rewriteFunction;
			return this;
		}

		public <T, R> Config setRewriteFunction(Class<T> inClass, Class<R> outClass,
				RewriteFunction<T, R> rewriteFunction) {
			setInClass(inClass);
			setOutClass(outClass);
			setRewriteFunction(rewriteFunction);
			return this;
		}

	}

	public class ModifyResponseGatewayFilter implements GatewayFilter, Ordered {

		private final Config config;

		private GatewayFilterFactory<Config> gatewayFilterFactory;

		public ModifyResponseGatewayFilter(Config config) {
			this.config = config;
		}

		@Override
		public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
			return chain.filter(exchange.mutate()
					.response(new ModifiedServerHttpResponse(exchange, config)).build());
		}

		@Override
		public int getOrder() {
			return NettyWriteResponseFilter.WRITE_RESPONSE_FILTER_ORDER - 1;
		}

		@Override
		public String toString() {
			Object obj = (this.gatewayFilterFactory != null) ? this.gatewayFilterFactory
					: this;
			return filterToStringCreator(obj)
					.append("New content type", config.getNewContentType())
					.append("In class", config.getInClass())
					.append("Out class", config.getOutClass()).toString();
		}

		public void setFactory(GatewayFilterFactory<Config> gatewayFilterFactory) {
			this.gatewayFilterFactory = gatewayFilterFactory;
		}

	}

	protected class ModifiedServerHttpResponse extends ServerHttpResponseDecorator {

		private final ServerWebExchange exchange;

		private final Config config;

		public ModifiedServerHttpResponse(ServerWebExchange exchange, Config config) {
			super(exchange.getResponse());
			this.exchange = exchange;
			this.config = config;
		}

		@SuppressWarnings("unchecked")
		@Override
		public Mono<Void> writeWith(Publisher<? extends DataBuffer> body) {

			Class inClass = config.getInClass();
			Class outClass = config.getOutClass();

			String originalResponseContentType = exchange
					.getAttribute(ORIGINAL_RESPONSE_CONTENT_TYPE_ATTR);
			HttpHeaders httpHeaders = new HttpHeaders();
			// explicitly add it in this way instead of
			// 'httpHeaders.setContentType(originalResponseContentType)'
			// this will prevent exception in case of using non-standard media
			// types like "Content-Type: image"
			httpHeaders.add(HttpHeaders.CONTENT_TYPE, originalResponseContentType);

			ClientResponse clientResponse = prepareClientResponse(body, httpHeaders);

			// TODO: flux or mono
			Mono modifiedBody = extractBody(exchange, clientResponse, inClass)
					.flatMap(originalBody -> config.getRewriteFunction().apply(exchange,
							originalBody))
					.switchIfEmpty(Mono.defer(() -> (Mono) config.getRewriteFunction()
							.apply(exchange, null)));

			BodyInserter bodyInserter = BodyInserters.fromPublisher(modifiedBody,
					outClass);
			CachedBodyOutputMessage outputMessage = new CachedBodyOutputMessage(exchange,
					exchange.getResponse().getHeaders());
			return bodyInserter.insert(outputMessage, new BodyInserterContext())
					.then(Mono.defer(() -> {
						Mono<DataBuffer> messageBody = writeBody(getDelegate(),
								outputMessage, outClass);
						HttpHeaders headers = getDelegate().getHeaders();
						if (!headers.containsKey(HttpHeaders.TRANSFER_ENCODING)
								|| headers.containsKey(HttpHeaders.CONTENT_LENGTH)) {
							messageBody = messageBody.doOnNext(data -> headers
									.setContentLength(data.readableByteCount()));
						}
						// TODO: fail if isStreamingMediaType?
						return getDelegate().writeWith(messageBody);
					}));
		}

		@Override
		public Mono<Void> writeAndFlushWith(
				Publisher<? extends Publisher<? extends DataBuffer>> body) {
			return writeWith(Flux.from(body).flatMapSequential(p -> p));
		}

		private ClientResponse prepareClientResponse(Publisher<? extends DataBuffer> body,
				HttpHeaders httpHeaders) {
			ClientResponse.Builder builder;
			builder = ClientResponse.create(exchange.getResponse().getStatusCode(),
					messageReaders);
			return builder.headers(headers -> headers.putAll(httpHeaders))
					.body(Flux.from(body)).build();
		}

		private <T> Mono<T> extractBody(ServerWebExchange exchange,
				ClientResponse clientResponse, Class<T> inClass) {
			// if inClass is byte[] then just return body, otherwise check if
			// decoding required
			if (byte[].class.isAssignableFrom(inClass)) {
				return clientResponse.bodyToMono(inClass);
			}

			List<String> encodingHeaders = exchange.getResponse().getHeaders()
					.getOrEmpty(HttpHeaders.CONTENT_ENCODING);
			for (String encoding : encodingHeaders) {
				MessageBodyDecoder decoder = messageBodyDecoders.get(encoding);
				if (decoder != null) {
					return clientResponse.bodyToMono(byte[].class)
							.publishOn(Schedulers.parallel()).map(decoder::decode)
							.map(bytes -> exchange.getResponse().bufferFactory()
									.wrap(bytes))
							.map(buffer -> prepareClientResponse(Mono.just(buffer),
									exchange.getResponse().getHeaders()))
							.flatMap(response -> response.bodyToMono(inClass));
				}
			}

			return clientResponse.bodyToMono(inClass);
		}

		private Mono<DataBuffer> writeBody(ServerHttpResponse httpResponse,
				CachedBodyOutputMessage message, Class<?> outClass) {
			Mono<DataBuffer> response = DataBufferUtils.join(message.getBody());
			if (byte[].class.isAssignableFrom(outClass)) {
				return response;
			}

			List<String> encodingHeaders = httpResponse.getHeaders()
					.getOrEmpty(HttpHeaders.CONTENT_ENCODING);
			for (String encoding : encodingHeaders) {
				MessageBodyEncoder encoder = messageBodyEncoders.get(encoding);
				if (encoder != null) {
					DataBufferFactory dataBufferFactory = httpResponse.bufferFactory();
					response = response.publishOn(Schedulers.parallel())
							.map(encoder::encode).map(dataBufferFactory::wrap);
					break;
				}
			}

			return response;
		}

	}

}
