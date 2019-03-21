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

package org.springframework.cloud.gateway.filter.factory.rewrite;

import java.util.Map;

import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.NettyWriteResponseFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.cloud.gateway.support.BodyInserterContext;
import org.springframework.cloud.gateway.support.CachedBodyOutputMessage;
import org.springframework.cloud.gateway.support.DefaultClientResponse;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseCookie;
import org.springframework.http.client.reactive.ClientHttpResponse;
import org.springframework.http.codec.ServerCodecConfigurer;
import org.springframework.http.server.reactive.ServerHttpResponseDecorator;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserter;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.server.ServerWebExchange;

import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.ORIGINAL_RESPONSE_CONTENT_TYPE_ATTR;

/**
 * This filter is BETA and may be subject to change in a future release.
 */
public class ModifyResponseBodyGatewayFilterFactory
		extends AbstractGatewayFilterFactory<ModifyResponseBodyGatewayFilterFactory.Config> {

	private final ServerCodecConfigurer codecConfigurer;

	public ModifyResponseBodyGatewayFilterFactory(ServerCodecConfigurer codecConfigurer) {
		super(Config.class);
		this.codecConfigurer = codecConfigurer;
	}

	@Override
	public GatewayFilter apply(Config config) {
		return new ModifyResponseGatewayFilter(config);
	}

	public class ModifyResponseGatewayFilter implements GatewayFilter, Ordered {
		private final Config config;

		public ModifyResponseGatewayFilter(Config config) {
			this.config = config;
		}

		@Override
		@SuppressWarnings("unchecked")
		public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {

			ServerHttpResponseDecorator responseDecorator = new ServerHttpResponseDecorator(exchange.getResponse()) {

				@Override
				public Mono<Void> writeWith(Publisher<? extends DataBuffer> body) {

					Class inClass = config.getInClass();
					Class outClass = config.getOutClass();

					String originalResponseContentType = exchange.getAttribute(ORIGINAL_RESPONSE_CONTENT_TYPE_ATTR);
					HttpHeaders httpHeaders = new HttpHeaders();
					//explicitly add it in this way instead of 'httpHeaders.setContentType(originalResponseContentType)'
					//this will prevent exception in case of using non-standard media types like "Content-Type: image"
					httpHeaders.add(HttpHeaders.CONTENT_TYPE, originalResponseContentType);
					ResponseAdapter responseAdapter = new ResponseAdapter(body, httpHeaders);
					DefaultClientResponse clientResponse = new DefaultClientResponse(responseAdapter, ExchangeStrategies.withDefaults());

					//TODO: flux or mono
					Mono modifiedBody = clientResponse.bodyToMono(inClass)
							.flatMap(originalBody -> config.rewriteFunction.apply(exchange, originalBody));

					BodyInserter bodyInserter = BodyInserters.fromPublisher(modifiedBody, outClass);
					CachedBodyOutputMessage outputMessage = new CachedBodyOutputMessage(exchange, exchange.getResponse().getHeaders());
					return bodyInserter.insert(outputMessage, new BodyInserterContext())
							.then(Mono.defer(() -> {
								long contentLength1 = getDelegate().getHeaders().getContentLength();
								Flux<DataBuffer> messageBody = outputMessage.getBody();
								//TODO: if (inputStream instanceof Mono) {
									HttpHeaders headers = getDelegate().getHeaders();
									if (/*headers.getContentLength() < 0 &&*/ !headers.containsKey(HttpHeaders.TRANSFER_ENCODING)) {
										messageBody = messageBody.doOnNext(data -> headers.setContentLength(data.readableByteCount()));
									}
								// }
								//TODO: use isStreamingMediaType?
								return getDelegate().writeWith(messageBody);
							}));
				}

				@Override
				public Mono<Void> writeAndFlushWith(Publisher<? extends Publisher<? extends DataBuffer>> body) {
					return writeWith(Flux.from(body)
							.flatMapSequential(p -> p));
				}
			};

			return chain.filter(exchange.mutate().response(responseDecorator).build());
		}

		@Override
		public int getOrder() {
			return NettyWriteResponseFilter.WRITE_RESPONSE_FILTER_ORDER - 1;
		}

	}

	public class ResponseAdapter implements ClientHttpResponse {

		private final Flux<DataBuffer> flux;
		private final HttpHeaders headers;

		public ResponseAdapter(Publisher<? extends DataBuffer> body, HttpHeaders headers) {
			this.headers = headers;
			if (body instanceof Flux) {
				flux = (Flux) body;
			} else {
				flux = ((Mono)body).flux();
			}
		}

		@Override
		public Flux<DataBuffer> getBody() {
			return flux;
		}

		@Override
		public HttpHeaders getHeaders() {
			return headers;
		}

		@Override
		public HttpStatus getStatusCode() {
			return null;
		}

		@Override
		public int getRawStatusCode() {
			return 0;
		}

		@Override
		public MultiValueMap<String, ResponseCookie> getCookies() {
			return null;
		}
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

		public <T, R> Config setRewriteFunction(Class<T> inClass, Class<R> outClass,
				RewriteFunction<T, R> rewriteFunction) {
			setInClass(inClass);
			setOutClass(outClass);
			setRewriteFunction(rewriteFunction);
			return this;
		}

		public Config setRewriteFunction(RewriteFunction rewriteFunction) {
			this.rewriteFunction = rewriteFunction;
			return this;
		}
	}
}
