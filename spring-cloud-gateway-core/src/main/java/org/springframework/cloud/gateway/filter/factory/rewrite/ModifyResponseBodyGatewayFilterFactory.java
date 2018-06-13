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

import static org.springframework.cloud.gateway.filter.factory.rewrite.RewriteUtils.getHttpMessageReader;
import static org.springframework.cloud.gateway.filter.factory.rewrite.RewriteUtils.getHttpMessageWriter;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;

import org.reactivestreams.Publisher;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.NettyWriteResponseFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.core.Ordered;
import org.springframework.core.ParameterizedTypeReference;
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

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Filter that do modification on response body.
 * 
 * @author Spencer Gibb
 * @author Anton Brok-Volchansky
 *
 */
@SuppressWarnings("rawtypes")
public class ModifyResponseBodyGatewayFilterFactory<T,R>
		extends AbstractGatewayFilterFactory<ModifyResponseBodyGatewayFilterFactory.Config> {

	private final ServerCodecConfigurer codecConfigurer;

	public ModifyResponseBodyGatewayFilterFactory(ServerCodecConfigurer codecConfigurer) {
		super(Config.class);
		this.codecConfigurer = codecConfigurer;
	}

	@SuppressWarnings("unchecked")
	@Override
	public GatewayFilter apply(Config config) {
		return new ModifyResponseGatewayFilter(config);
	}

	public class ModifyResponseGatewayFilter implements GatewayFilter, Ordered {
		private final Config<T, R> config;

		public ModifyResponseGatewayFilter(Config<T, R> config) {
			this.config = config;
		}

		@Override
		@SuppressWarnings("unchecked")
		public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
			
			ServerHttpResponseDecorator responseDecorator = new ServerHttpResponseDecorator(exchange.getResponse()) {
				
				@Override
				public HttpHeaders getHeaders() {
					
					if(config.getOutClass().isAssignableFrom(config.getInClass())) {
						 return getDelegate().getHeaders();
					}

					HttpHeaders headers = new HttpHeaders();
					headers.putAll(getDelegate().getHeaders());
					
					headers.setContentType(config.getOutMediaType());
					headers.setAccept(Collections.singletonList(config.getOutMediaType()));
					
					return headers;
				}

				
				@Override
				public Mono<Void> writeWith(Publisher<? extends DataBuffer> body) {

					ResolvableType inElementType = config.getInClass();
					ResolvableType outElementType = config.getOutClass();
					
					MediaType contentType = getDelegate().getHeaders().getContentType();
					MediaType outMediaType = config.getOutMediaType() == null ? contentType : config.getOutMediaType();
					
					Optional<HttpMessageReader<?>> reader = getHttpMessageReader(codecConfigurer, inElementType, contentType);
					Optional<HttpMessageWriter<?>> writer = getHttpMessageWriter(codecConfigurer, outElementType, outMediaType);

					if (reader.isPresent() && writer.isPresent()) {

						ResponseAdapter responseAdapter = new ResponseAdapter(body, getDelegate().getHeaders());
						
						RewriteFunction<T, R> rewriteFunction = config.rewriteFunction;
						
						Flux<?> modified = reader.get().read(inElementType, responseAdapter, config.getInHints())
								.cast(inElementType.resolve())
								.flatMap(originalBody -> {
									return Flux.just(rewriteFunction.apply(exchange, (T) originalBody));
								})
								.cast(outElementType.resolve());

						return getDelegate().writeWith(
								writer.get().write((Publisher) modified, outElementType, outMediaType, getDelegate(),
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

		@SuppressWarnings("unchecked")
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

	public static class Config<T, R> {

		private ResolvableType elementTypeIn;
		private ResolvableType elementTypeOut;

		private Map<String, Object> inHints;
		private Map<String, Object> outHints;
		
		private MediaType outMediaType;

		private RewriteFunction<T, R> rewriteFunction;

		public ResolvableType getInClass() {
			return elementTypeIn;
		}

		public Config<T, R> setInClass(Class<?> inClass) {
			this.elementTypeIn = ResolvableType.forClass(inClass);
			return this;
		}
		
		public Config<T, R> setInClass(ResolvableType elementTypeIn) {
			this.elementTypeIn = elementTypeIn;
			return this;
		}
		
		public Config<T, R> setInClass(ParameterizedTypeReference<?> inTypeRef) {
			this.elementTypeIn = ResolvableType.forType(inTypeRef.getType());
			return this;
		}

		public ResolvableType getOutClass() {
			return elementTypeOut;
		}

		public Config<T, R> setOutClass(Class<?> outClass) {
			this.elementTypeOut = ResolvableType.forClass(outClass);
			return this;
		}
		
		public Config<T, R> setOutClass(ResolvableType elementTypeOut) {
			this.elementTypeOut = elementTypeOut;
			return this;
		}
		
		public Config<T, R> setOutClass(ParameterizedTypeReference<?> outTypeRef) {
			this.elementTypeOut = ResolvableType.forType(outTypeRef.getType());
			return this;
		}

		public Map<String, Object> getInHints() {
			return inHints;
		}

		public Config<T, R> setInHints(Map<String, Object> inHints) {
			this.inHints = inHints;
			return this;
		}

		public Map<String, Object> getOutHints() {
			return outHints;
		}

		public Config<T, R> setOutHints(Map<String, Object> outHints) {
			this.outHints = outHints;
			return this;
		}
		
		public MediaType getOutMediaType() {
			return outMediaType;
		}
		
		public Config<T, R> setOutMediaType(MediaType outMediaType) {
			this.outMediaType = outMediaType;
			return this;
		}

		public RewriteFunction<T, R> getRewriteFunction() {
			return rewriteFunction;
		}

		public Config<T, R> setRewriteFunction(Class<T> inClass, Class<R> outClass,
				RewriteFunction<T, R> rewriteFunction) {
			return setRewriteFunction(inClass, outClass, rewriteFunction, Collections.emptyMap(), Collections.emptyMap(), null);
		}
		
		public  Config<T, R> setRewriteFunction(Class<T> inClass, Class<R> outClass,
				RewriteFunction<T, R> rewriteFunction, Map<String, Object> inHints, Map<String, Object> outHints, MediaType outMediaType) {
			setInClass(inClass);
			setOutClass(outClass);
			setRewriteFunction(rewriteFunction);
			setInHints(inHints);
			setOutHints(outHints);
			setOutMediaType(outMediaType);
			return this;
		}

		public Config<T, R> setRewriteFunction(RewriteFunction<T, R> rewriteFunction) {
			this.rewriteFunction = (RewriteFunction<T, R>) rewriteFunction;
			return this;
		}
		
		
	}
}
