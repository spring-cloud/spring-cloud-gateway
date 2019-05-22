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

import java.util.Map;

import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.NettyWriteResponseFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.cloud.gateway.support.BodyInserterContext;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.codec.ServerCodecConfigurer;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.http.server.reactive.ServerHttpResponseDecorator;
import org.springframework.web.reactive.function.BodyInserter;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.server.ServerWebExchange;

import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.ORIGINAL_RESPONSE_CONTENT_TYPE_ATTR;

/**
 * This filter is BETA and may be subject to change in a future release.
 */
public class ModifyResponseBodyGatewayFilterFactory extends
		AbstractGatewayFilterFactory<ModifyResponseBodyGatewayFilterFactory.Config> {

	public ModifyResponseBodyGatewayFilterFactory() {
		super(Config.class);
	}

	@Deprecated
	public ModifyResponseBodyGatewayFilterFactory(ServerCodecConfigurer codecConfigurer) {
		this();
	}

	@Override
	public GatewayFilter apply(Config config) {
		return new ModifyResponseGatewayFilter(config);
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

		public ModifyResponseGatewayFilter(Config config) {
			this.config = config;
		}

		@Override

		public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
			return chain.filter(exchange.mutate().response(decorate(exchange)).build());
		}

		@SuppressWarnings("unchecked")
		ServerHttpResponse decorate(ServerWebExchange exchange) {
			return new ServerHttpResponseDecorator(exchange.getResponse()) {

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
					httpHeaders.add(HttpHeaders.CONTENT_TYPE,
							originalResponseContentType);

					ClientResponse clientResponse = ClientResponse
							.create(exchange.getResponse().getStatusCode())
							.headers(headers -> headers.putAll(httpHeaders))
							.body(Flux.from(body)).build();

					// TODO: flux or mono
					Mono modifiedBody = clientResponse.bodyToMono(inClass)
							.flatMap(originalBody -> config.rewriteFunction
									.apply(exchange, originalBody));

					BodyInserter bodyInserter = BodyInserters.fromPublisher(modifiedBody,
							outClass);
					CachedBodyOutputMessage outputMessage = new CachedBodyOutputMessage(
							exchange, exchange.getResponse().getHeaders());
					return bodyInserter.insert(outputMessage, new BodyInserterContext())
							.then(Mono.defer(() -> {
								Flux<DataBuffer> messageBody = outputMessage.getBody();
								HttpHeaders headers = getDelegate().getHeaders();
								if (!headers.containsKey(HttpHeaders.TRANSFER_ENCODING)) {
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
			};
		}

		@Override
		public int getOrder() {
			return NettyWriteResponseFilter.WRITE_RESPONSE_FILTER_ORDER - 1;
		}

	}

}
