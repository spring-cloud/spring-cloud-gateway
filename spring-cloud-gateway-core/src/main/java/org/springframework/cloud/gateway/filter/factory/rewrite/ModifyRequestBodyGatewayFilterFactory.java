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

import org.springframework.cloud.gateway.support.BodyInserterContext;
import org.springframework.cloud.gateway.support.CachedBodyOutputMessage;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.cloud.gateway.support.DefaultServerRequest;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.codec.ServerCodecConfigurer;
import org.springframework.http.server.reactive.ServerHttpRequestDecorator;
import org.springframework.web.reactive.function.BodyInserter;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.server.ServerRequest;

/**
 * This filter is BETA and may be subject to change in a future release.
 */
public class ModifyRequestBodyGatewayFilterFactory
		extends AbstractGatewayFilterFactory<ModifyRequestBodyGatewayFilterFactory.Config> {

	public ModifyRequestBodyGatewayFilterFactory() {
		super(Config.class);
	}

	@Deprecated
	public ModifyRequestBodyGatewayFilterFactory(ServerCodecConfigurer codecConfigurer) {
		this();
	}

	@Override
	@SuppressWarnings("unchecked")
	public GatewayFilter apply(Config config) {
		return (exchange, chain) -> {
			Class inClass = config.getInClass();

			ServerRequest serverRequest = new DefaultServerRequest(exchange);
			//TODO: flux or mono
			Mono<?> modifiedBody = serverRequest.bodyToMono(inClass)
					// .log("modify_request_mono", Level.INFO)
					.flatMap(o -> config.rewriteFunction.apply(exchange, o));

			BodyInserter bodyInserter = BodyInserters.fromPublisher(modifiedBody, config.getOutClass());
			HttpHeaders headers = new HttpHeaders();
			headers.putAll(exchange.getRequest().getHeaders());
			CachedBodyOutputMessage outputMessage = new CachedBodyOutputMessage(exchange, headers);
			return bodyInserter.insert(outputMessage,  new BodyInserterContext())
					// .log("modify_request", Level.INFO)
					.then(Mono.defer(() -> {
						ServerHttpRequestDecorator decorator = new ServerHttpRequestDecorator(
								exchange.getRequest()) {
							@Override
							public HttpHeaders getHeaders() {
								long contentLength = headers.getContentLength();
								HttpHeaders httpHeaders = new HttpHeaders();
								httpHeaders.putAll(super.getHeaders());
								if (contentLength >= 0) {
									httpHeaders.setContentLength(contentLength);
								} else {
									// TODO: this causes a 'HTTP/1.1 411 Length Required' on httpbin.org
									httpHeaders.set(HttpHeaders.TRANSFER_ENCODING, "chunked");
								}
								return httpHeaders;
							}

							@Override
							public Flux<DataBuffer> getBody() {
								return outputMessage.getBody();
							}
						};
						return chain.filter(exchange.mutate().request(decorator).build());
					}));

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
