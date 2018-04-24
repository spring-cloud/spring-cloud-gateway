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

package org.springframework.cloud.gateway.filter.factory.rewrite;

import java.util.Map;
import java.util.Optional;

import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.NettyWriteResponseFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.core.Ordered;
import org.springframework.core.ResolvableType;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ReactiveHttpInputMessage;
import org.springframework.http.codec.HttpMessageReader;
import org.springframework.http.codec.HttpMessageWriter;
import org.springframework.http.codec.ServerCodecConfigurer;
import org.springframework.http.server.reactive.ServerHttpResponseDecorator;
import org.springframework.web.server.ServerWebExchange;

import static org.springframework.cloud.gateway.filter.factory.rewrite.RewriteUtils.getHttpMessageReader;
import static org.springframework.cloud.gateway.filter.factory.rewrite.RewriteUtils.getHttpMessageWriter;

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

					ResolvableType inElementType = ResolvableType.forClass(config.getInClass());
					ResolvableType outElementType = ResolvableType.forClass(config.getOutClass());
					MediaType contentType = exchange.getResponse().getHeaders().getContentType();
					Optional<HttpMessageReader<?>> reader = getHttpMessageReader(codecConfigurer, inElementType, contentType);
					Optional<HttpMessageWriter<?>> writer = getHttpMessageWriter(codecConfigurer, outElementType, null);

					if (reader.isPresent() && writer.isPresent()) {

						ResponseAdapter responseAdapter = new ResponseAdapter(body, getDelegate().getHeaders());

						Flux<?> modified = reader.get().read(inElementType, responseAdapter, config.getInHints())
								.cast(inElementType.resolve())
								.flatMap(originalBody -> Flux.just(config.rewriteFunction.apply(exchange, originalBody)))
								.cast(outElementType.resolve());

						return getDelegate().writeWith(
								writer.get().write((Publisher)modified, outElementType, null, getDelegate(),
										config.getOutHints())
						);

					}
					// TODO: error? log?

					return getDelegate().writeWith(body);
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

	public class ResponseAdapter implements ReactiveHttpInputMessage {

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
	}

	public static class Config {
		private Class inClass;
		private Class outClass;
		private Map<String, Object> inHints;
		private Map<String, Object> outHints;

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
