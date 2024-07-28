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
import org.springframework.util.StringUtils;
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
public class ModifyResponseBodyGatewayFilterFactory
		extends AbstractGatewayFilterFactory<ModifyResponseBodyGatewayFilterFactory.Config> {

	private final Map<String, MessageBodyDecoder> messageBodyDecoders;

	private final Map<String, MessageBodyEncoder> messageBodyEncoders;

	private final List<HttpMessageReader<?>> messageReaders;

	public ModifyResponseBodyGatewayFilterFactory(List<HttpMessageReader<?>> messageReaders,
			Set<MessageBodyDecoder> messageBodyDecoders, Set<MessageBodyEncoder> messageBodyEncoders) {
		super(Config.class);
		this.messageReaders = messageReaders;
		this.messageBodyDecoders = messageBodyDecoders.stream()
			.collect(Collectors.toMap(MessageBodyDecoder::encodingType, identity()));
		this.messageBodyEncoders = messageBodyEncoders.stream()
			.collect(Collectors.toMap(MessageBodyEncoder::encodingType, identity()));
	}

	@Override
	public GatewayFilter apply(Config config) {
		ModifyResponseGatewayFilter gatewayFilter = new ModifyResponseGatewayFilter(config);
		gatewayFilter.setFactory(this);
		return gatewayFilter;
	}

	public static class Config {

		private Class inClass;

		private Class outClass;

		private Map<String, Object> inHints;

		private Map<String, Object> outHints;

		private String newContentType;

		/**
		 * Deprecated in favour of {@link FluxRewriteFunction} &
		 * {@link MonoRewriteFunction} Use {@link MonoRewriteFunction} for modifying
		 * non-streaming response body Use {@link FluxRewriteFunction} for modifying
		 * streaming response body
		 */
		@Deprecated
		private RewriteFunction rewriteFunction;

		private FluxRewriteFunction fluxRewriteFunction;

		private MonoRewriteFunction monoRewriteFunction;

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

		@Deprecated
		public RewriteFunction getRewriteFunction() {
			return rewriteFunction;
		}

		public <T, R> MonoRewriteFunction<Mono<T>, Mono<R>> getMonoRewriteFunction() {
			return monoRewriteFunction;
		}

		public <T, R> FluxRewriteFunction<Flux<T>, Flux<R>> getFluxRewriteFunction() {
			return fluxRewriteFunction;
		}

		/**
		 * Deprecated in favour of {@link Config#setMonoRewriteFunction} &
		 * {@link Config#setFluxRewriteFunction} Use {@link Config#setMonoRewriteFunction}
		 * for modifying non-streaming response body Use
		 * {@link Config#setFluxRewriteFunction} for modifying streaming response body
		 */
		@Deprecated
		public Config setRewriteFunction(RewriteFunction rewriteFunction) {
			this.rewriteFunction = rewriteFunction;
			return this;
		}

		public Config setMonoRewriteFunction(MonoRewriteFunction monoRewriteFunction) {
			this.monoRewriteFunction = monoRewriteFunction;
			return this;
		}

		public Config setFluxRewriteFunction(FluxRewriteFunction fluxRewriteFunction) {
			this.fluxRewriteFunction = fluxRewriteFunction;
			return this;
		}

		@Deprecated
		public <T, R> Config setRewriteFunction(Class<T> inClass, Class<R> outClass,
				RewriteFunction<T, R> rewriteFunction) {
			setInClass(inClass);
			setOutClass(outClass);
			setRewriteFunction(rewriteFunction);
			return this;
		}

		public <T, R> Config setFluxRewriteFunction(Class<T> inClass, Class<R> outClass,
				FluxRewriteFunction<T, R> fluxRewriteFunction) {
			setInClass(inClass);
			setOutClass(outClass);
			setFluxRewriteFunction(fluxRewriteFunction);
			return this;
		}

		public <T, R> Config setMonoRewriteFunction(Class<T> inClass, Class<R> outClass,
				MonoRewriteFunction<T, R> monoRewriteFunction) {
			setInClass(inClass);
			setOutClass(outClass);
			setMonoRewriteFunction(monoRewriteFunction);
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
			return chain.filter(exchange.mutate().response(new ModifiedServerHttpResponse(exchange, config)).build());
		}

		@Override
		public int getOrder() {
			return NettyWriteResponseFilter.WRITE_RESPONSE_FILTER_ORDER - 1;
		}

		@Override
		public String toString() {
			Object obj = (this.gatewayFilterFactory != null) ? this.gatewayFilterFactory : this;
			return filterToStringCreator(obj).append("New content type", config.getNewContentType())
				.append("In class", config.getInClass())
				.append("Out class", config.getOutClass())
				.toString();
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

		@Override
		@SuppressWarnings({ "unchecked", "rawtypes" })
		public Mono<Void> writeWith(Publisher<? extends DataBuffer> body) {

			Class inClass = config.getInClass();
			Class outClass = config.getOutClass();

			HttpHeaders httpHeaders = prepareHttpHeaders();
			ClientResponse clientResponse = prepareClientResponse(body, httpHeaders);

			var modifiedBody = extractBody(exchange, clientResponse, inClass);
			if (config.getRewriteFunction() != null) {
				// TODO: to be removed with removal of rewriteFunction
				modifiedBody = modifiedBody
						.flatMap(originalBody -> config.getRewriteFunction()
								.apply(exchange, originalBody))
						.switchIfEmpty(Mono.defer(() -> (Mono) config.getRewriteFunction()
								.apply(exchange, null)));
			}
			if (config.getMonoRewriteFunction() != null) {
				modifiedBody = config.getMonoRewriteFunction().apply(exchange,
						modifiedBody);
			}

			BodyInserter bodyInserter = BodyInserters.fromPublisher(modifiedBody,
					outClass);
			CachedBodyOutputMessage outputMessage = new CachedBodyOutputMessage(exchange,
					exchange.getResponse().getHeaders());
			return bodyInserter.insert(outputMessage, new BodyInserterContext())
					.then(Mono.defer(() -> {
						Mono<DataBuffer> messageBody = writeBody(getDelegate(), outputMessage, outClass);
						HttpHeaders headers = getDelegate().getHeaders();
						if (!headers.containsKey(HttpHeaders.TRANSFER_ENCODING)
								|| headers.containsKey(HttpHeaders.CONTENT_LENGTH)) {
							messageBody = messageBody.doOnNext(data -> headers.setContentLength(data.readableByteCount()));
						}

						if (StringUtils.hasText(config.newContentType)) {
							headers.set(HttpHeaders.CONTENT_TYPE, config.newContentType);
						}

						// TODO: fail if isStreamingMediaType?
						return getDelegate().writeWith(messageBody);
					}));
		}

		@Override
		@SuppressWarnings({ "unchecked", "rawtypes" })
		public Mono<Void> writeAndFlushWith(
				Publisher<? extends Publisher<? extends DataBuffer>> body) {
			final var httpHeaders = prepareHttpHeaders();
			final var fluxRewriteConfig = config.getFluxRewriteFunction();
			final var publisher = Flux.from(body).flatMapSequential(r -> r);
			final var clientResponse = prepareClientResponse(publisher, httpHeaders);
			var modifiedBody = clientResponse.bodyToFlux(config.inClass);
			if (config.getRewriteFunction() != null) {
				// TODO: to be removed with removal of rewriteFunction
				modifiedBody = modifiedBody
						.flatMap(originalBody -> config.getRewriteFunction()
								.apply(exchange, originalBody))
						.switchIfEmpty(Flux.defer(() -> (Flux) config.getRewriteFunction()
								.apply(exchange, null)));
			}
			if (config.getFluxRewriteFunction() != null) {
				modifiedBody = fluxRewriteConfig.apply(exchange, modifiedBody);
			}
			final var bodyInserter = BodyInserters.fromPublisher(modifiedBody,
					config.outClass);
			final var outputMessage = new CachedBodyOutputMessage(exchange,
					exchange.getResponse().getHeaders());

			return bodyInserter.insert(outputMessage, new BodyInserterContext())
					.then(Mono.defer(() -> {
						final var messageBody = outputMessage.getBody();
						HttpHeaders headers = getDelegate().getHeaders();
						if (StringUtils.hasText(config.newContentType)) {
							headers.set(HttpHeaders.CONTENT_TYPE, config.newContentType);
						}
						return getDelegate()
								.writeAndFlushWith(messageBody.map(Flux::just));
					}));
		}

		private HttpHeaders prepareHttpHeaders() {
			String originalResponseContentType = exchange
					.getAttribute(ORIGINAL_RESPONSE_CONTENT_TYPE_ATTR);
			HttpHeaders httpHeaders = new HttpHeaders();
			// explicitly add it in this way instead of
			// 'httpHeaders.setContentType(originalResponseContentType)'
			// this will prevent exception in case of using non-standard media
			// types like "Content-Type: image"
			httpHeaders.add(HttpHeaders.CONTENT_TYPE, originalResponseContentType);
			return httpHeaders;
		}

		private ClientResponse prepareClientResponse(Publisher<? extends DataBuffer> body, HttpHeaders httpHeaders) {
			ClientResponse.Builder builder;
			builder = ClientResponse.create(exchange.getResponse().getStatusCode(), messageReaders);
			return builder.headers(headers -> headers.putAll(httpHeaders)).body(Flux.from(body)).build();
		}

		private <T> Mono<T> extractBody(ServerWebExchange exchange, ClientResponse clientResponse, Class<T> inClass) {
			// if inClass is byte[] then just return body, otherwise check if
			// decoding required
			if (byte[].class.isAssignableFrom(inClass)) {
				return clientResponse.bodyToMono(inClass);
			}

			List<String> encodingHeaders = exchange.getResponse().getHeaders().getOrEmpty(HttpHeaders.CONTENT_ENCODING);
			for (String encoding : encodingHeaders) {
				MessageBodyDecoder decoder = messageBodyDecoders.get(encoding);
				if (decoder != null) {
					return clientResponse.bodyToMono(byte[].class)
						.publishOn(Schedulers.parallel())
						.map(decoder::decode)
						.map(bytes -> exchange.getResponse().bufferFactory().wrap(bytes))
						.map(buffer -> prepareClientResponse(Mono.just(buffer), exchange.getResponse().getHeaders()))
						.flatMap(response -> response.bodyToMono(inClass));
				}
			}

			return clientResponse.bodyToMono(inClass);
		}

		private Mono<DataBuffer> writeBody(ServerHttpResponse httpResponse, CachedBodyOutputMessage message,
				Class<?> outClass) {
			Mono<DataBuffer> response = DataBufferUtils.join(message.getBody());
			if (byte[].class.isAssignableFrom(outClass)) {
				return response;
			}

			List<String> encodingHeaders = httpResponse.getHeaders().getOrEmpty(HttpHeaders.CONTENT_ENCODING);
			for (String encoding : encodingHeaders) {
				MessageBodyEncoder encoder = messageBodyEncoders.get(encoding);
				if (encoder != null) {
					DataBufferFactory dataBufferFactory = httpResponse.bufferFactory();
					response = response.publishOn(Schedulers.parallel()).map(buffer -> {
						byte[] encodedResponse = encoder.encode(buffer);
						DataBufferUtils.release(buffer);
						return encodedResponse;
					}).map(dataBufferFactory::wrap);
					break;
				}
			}

			return response;
		}

	}

}
