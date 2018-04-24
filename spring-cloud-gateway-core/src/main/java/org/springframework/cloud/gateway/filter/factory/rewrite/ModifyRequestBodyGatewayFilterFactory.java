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
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.core.ResolvableType;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.codec.HttpMessageReader;
import org.springframework.http.codec.HttpMessageWriter;
import org.springframework.http.codec.ServerCodecConfigurer;
import org.springframework.http.server.reactive.ServerHttpRequestDecorator;

import static org.springframework.cloud.gateway.filter.factory.rewrite.RewriteUtils.process;

public class ModifyRequestBodyGatewayFilterFactory
		extends AbstractGatewayFilterFactory<ModifyRequestBodyGatewayFilterFactory.Config> {

	private final ServerCodecConfigurer codecConfigurer;

	public ModifyRequestBodyGatewayFilterFactory(ServerCodecConfigurer codecConfigurer) {
		super(Config.class);
		this.codecConfigurer = codecConfigurer;
	}

	@Override
	@SuppressWarnings("unchecked")
	public GatewayFilter apply(Config config) {
		return (exchange, chain) -> {
			Class inClass = config.getInClass();

			MediaType mediaType = exchange.getRequest().getHeaders().getContentType();
			ResolvableType inElementType = ResolvableType.forClass(inClass);
			Optional<HttpMessageReader<?>> reader = RewriteUtils.getHttpMessageReader(codecConfigurer, inElementType, mediaType);

			if (reader.isPresent()) {
				Mono<Object> readMono = reader.get()
						.readMono(inElementType, exchange.getRequest(), config.getInHints())
						.cast(Object.class);

				return process(readMono, peek -> {
					ResolvableType outElementType = ResolvableType
							.forClass(config.getOutClass());
					Optional<HttpMessageWriter<?>> writer = RewriteUtils.getHttpMessageWriter(codecConfigurer, outElementType, mediaType);

					if (writer.isPresent()) {
						Object data = config.rewriteFunction.apply(exchange, peek);

						//TODO: deal with multivalue? ie Flux
						Publisher publisher = Mono.just(data);

						HttpMessageWriterResponse fakeResponse = new HttpMessageWriterResponse(exchange.getResponse().bufferFactory());
						writer.get().write(publisher, inElementType, mediaType,
								fakeResponse, config.getOutHints());
						ServerHttpRequestDecorator decorator = new ServerHttpRequestDecorator(
								exchange.getRequest()) {
							@Override
							public HttpHeaders getHeaders() {
								HttpHeaders httpHeaders = new HttpHeaders();
								httpHeaders.putAll(super.getHeaders());
								// TODO: this causes a 'HTTP/1.1 411 Length Required' on
								// httpbin.org
								httpHeaders.set(HttpHeaders.TRANSFER_ENCODING, "chunked");
								if (fakeResponse.getHeaders().getContentType() != null) {
									httpHeaders.setContentType(
											fakeResponse.getHeaders().getContentType());
								}
								return httpHeaders;
							}

							@Override
							public Flux<DataBuffer> getBody() {
								return (Flux<DataBuffer>) fakeResponse.getBody();
							}
						};
						return chain.filter(exchange.mutate().request(decorator).build());
					}
					return chain.filter(exchange);
				});

			}
			return chain.filter(exchange);
		};
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
