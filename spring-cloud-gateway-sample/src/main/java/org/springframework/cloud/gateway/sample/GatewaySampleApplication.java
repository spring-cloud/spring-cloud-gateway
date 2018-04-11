/*
 * Copyright 2013-2017 the original author or authors.
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

package org.springframework.cloud.gateway.sample;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;

import org.reactivestreams.Publisher;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequestDecorator;
import org.springframework.http.server.reactive.ServerHttpResponse;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.MonoProcessor;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.cloud.gateway.handler.predicate.AbstractRoutePredicateFactory;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.ResolvableType;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.MediaType;
import org.springframework.http.codec.HttpMessageReader;
import org.springframework.http.codec.HttpMessageWriter;
import org.springframework.http.codec.ServerCodecConfigurer;
import org.springframework.http.server.reactive.ServerHttpResponseDecorator;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.server.RequestPredicates;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.server.ServerWebExchange;

/**
 * @author Spencer Gibb
 */
@SpringBootConfiguration
@EnableAutoConfiguration
@Import(AdditionalRoutes.class)
public class GatewaySampleApplication {

	@Bean
	public RouteLocator customRouteLocator(RouteLocatorBuilder builder) {
		//@formatter:off
		// String uri = "http://httpbin.org:80";
		String uri = "http://localhost:9080";
		return builder.routes()
				.route(r -> r.host("**.abc.org").and().path("/image/png")
					.filters(f ->
							f.addResponseHeader("X-TestHeader", "foobar"))
					.uri(uri)
				)
				.route("read_body_pred", r -> r.host("*.readbody.org")
						.and().predicate(readBodyPredicateFactory().apply(o -> {}))
					.filters(f ->
							f.addRequestHeader("X-TestHeader", "read_body_pred")
					).uri(uri)
				)
				.route("rewrite_request_obj", r -> r.host("*.rewriterequestobj.org")
					.filters(f -> f.addRequestHeader("X-TestHeader", "rewrite_request")
							.filter(modifyRequestBodyPredicateFactory().apply(c ->
									c.setRewriteFunction(String.class, Hello.class,
									(exchange, s) -> {
                                        return new Hello(s.toUpperCase());
                                    })))
					).uri(uri)
				)
                .route("rewrite_request_upper", r -> r.host("*.rewriterequestupper.org")
					.filters(f -> f.addRequestHeader("X-TestHeader", "rewrite_request_upper")
							.filter(modifyRequestBodyPredicateFactory().apply(c ->
									c.setRewriteFunction(String.class, String.class,
									(exchange, s) -> {
                                        return s.toUpperCase();
                                    })))
					).uri(uri)
				)
				.route("rewrite_response", r -> r.host("*.rewriteresponse.org")
					.filters(f -> f.addRequestHeader("X-TestHeader", "rewrite_response")
							/*TODO: .filter()*/)
						.uri(uri)
				)
				.route(r -> r.path("/image/webp")
					.filters(f ->
							f.addResponseHeader("X-AnotherHeader", "baz"))
					.uri(uri)
				)
				.route(r -> r.order(-1)
					.host("**.throttle.org").and().path("/get")
					.filters(f -> f.filter(new ThrottleGatewayFilter()
									.setCapacity(1)
									.setRefillTokens(1)
									.setRefillPeriod(10)
									.setRefillUnit(TimeUnit.SECONDS)))
					.uri(uri)
				)
				.build();
		//@formatter:on
	}

	@Bean
	public RouterFunction<ServerResponse> testFunRouterFunction() {
		RouterFunction<ServerResponse> route = RouterFunctions.route(
				RequestPredicates.path("/testfun"),
				request -> ServerResponse.ok().body(BodyInserters.fromObject("hello")));
		return route;
	}

	@Bean
	public ReadBodyPredicateFactory readBodyPredicateFactory() {
		return new ReadBodyPredicateFactory();
	}

	@Bean
	public ModifyRequestBodyPredicateFactory modifyRequestBodyPredicateFactory() {
		return new ModifyRequestBodyPredicateFactory();
	}

	static class ModifyRequestBodyPredicateFactory extends AbstractGatewayFilterFactory<ModifyRequestBodyPredicateFactory.Config> {

		@Autowired
		ServerCodecConfigurer serverCodecConfigurer;

		public ModifyRequestBodyPredicateFactory() {
			super(Config.class);
		}

		@Override
		@SuppressWarnings("unchecked")
		public GatewayFilter apply(Config config) {
			return (exchange, chain) -> {
				Class inClass = config.getInClass();

				MediaType mediaType = exchange.getRequest().getHeaders().getContentType();
				List<HttpMessageReader<?>> readers = serverCodecConfigurer.getReaders();
				ResolvableType inElementType = ResolvableType.forClass(inClass);
				Optional<HttpMessageReader<?>> reader = readers.stream()
						.filter(r -> r.canRead(inElementType, mediaType))
						.findFirst();

				if (reader.isPresent()) {
					Mono<Object> readMono = reader.get()
							.readMono(inElementType, exchange.getRequest(), null)
							.cast(Object.class);

					return process(readMono, peek -> {
						ResolvableType outElementType = ResolvableType.forClass(config.getOutClass());
						Optional<HttpMessageWriter<?>> writer = serverCodecConfigurer.getWriters()
								.stream()
								.filter(w -> w.canWrite(outElementType, mediaType))
								.findFirst();

						if (writer.isPresent()) {
							Object data = config.rewriteFunction.apply(exchange, peek);

							Publisher publisher = Mono.just(data);
							FakeResponse fakeResponse = new FakeResponse(exchange.getResponse());
							writer.get().write(publisher, inElementType, mediaType, fakeResponse, null);
                            ServerHttpRequestDecorator decorator = new ServerHttpRequestDecorator(exchange.getRequest()) {
								@Override
								public HttpHeaders getHeaders() {
									HttpHeaders httpHeaders = new HttpHeaders();
									httpHeaders.putAll(super.getHeaders());
									//TODO: this causes a 'HTTP/1.1 411 Length Required' on httpbin.org
									httpHeaders.set(HttpHeaders.TRANSFER_ENCODING, "chunked");
									if (fakeResponse.getHeaders().getContentType() != null) {
										httpHeaders.setContentType(fakeResponse.getHeaders().getContentType());
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

			public RewriteFunction getRewriteFunction() {
				return rewriteFunction;
			}

			public <T, R> Config setRewriteFunction(Class<T> inClass, Class<R> outClass, RewriteFunction<T, R> rewriteFunction) {
				setInClass(inClass);
				setRewriteFunction(rewriteFunction);
				return this;
			}

			public Config setRewriteFunction(RewriteFunction rewriteFunction) {
				this.rewriteFunction = rewriteFunction;
				return this;
			}
		}
	}

	static class FakeResponse extends ServerHttpResponseDecorator  {

		private Publisher<? extends DataBuffer> body;

		public FakeResponse(ServerHttpResponse delegate) {
			super(delegate);
		}

		@Override
		public Mono<Void> writeWith(Publisher<? extends DataBuffer> body) {
			this.body = body;
			return Mono.empty();
		}

		@Override
		public Mono<Void> writeAndFlushWith(Publisher<? extends Publisher<? extends DataBuffer>> body) {
			throw new UnsupportedOperationException("writeAndFlushWith");
		}

		public Publisher<? extends DataBuffer> getBody() {
			return body;
		}
	}

	interface RewriteFunction<T, R> extends BiFunction<ServerWebExchange, T, R> {
	}

	static class Hello {
		String message;

		public Hello() { }

		public Hello(String message) {
			this.message = message;
		}

		public String getMessage() {
			return message;
		}

		public void setMessage(String message) {
			this.message = message;
		}
	}

	static class ReadBodyPredicateFactory extends AbstractRoutePredicateFactory {

		@Autowired
		ServerCodecConfigurer serverCodecConfigurer;

		public ReadBodyPredicateFactory() {
			super(Object.class);
		}

		@Override
		@SuppressWarnings("unchecked")
		public Predicate<ServerWebExchange> apply(Object config) {
			return exchange -> {
				MediaType mediaType = exchange.getRequest().getHeaders().getContentType();
				List<HttpMessageReader<?>> readers = serverCodecConfigurer.getReaders();
				ResolvableType elementType = ResolvableType.forClass(String.class);
				Optional<HttpMessageReader<?>> reader = readers.stream()
						.filter(r -> r.canRead(elementType, mediaType))
						.findFirst();
				boolean answer = false;
                if (reader.isPresent()) {
					Mono<String> readMono = reader.get().readMono(elementType, exchange.getRequest(), null)
							.cast(String.class);
					answer = process(readMono, peek -> {
						Optional<HttpMessageWriter<?>> writer = serverCodecConfigurer.getWriters()
								.stream()
								.filter(w -> w.canWrite(elementType, mediaType))
								.findFirst();

						if (writer.isPresent()) {
							Publisher publisher = Mono.just(peek);
							FakeResponse fakeResponse = new FakeResponse(exchange.getResponse());
							writer.get().write(publisher, elementType, mediaType, fakeResponse, null);
							exchange.getAttributes().put("cachedRequestBody", fakeResponse.getBody());
						}
						//TODO: make generic
						return peek.trim().equalsIgnoreCase("hello");
					});

				}
				return answer;
			};
		}
	}

	public static <T, R> R process(Mono<T> mono, Function<T, R> consumer) {
		MonoProcessor<T> processor = MonoProcessor.create();
		mono.subscribeWith(processor);
		if (processor.isTerminated()) {
			Throwable error = processor.getError();
			if (error != null) {
				throw (RuntimeException) error;
			}
			T peek = processor.peek();

			return consumer.apply(peek);
		}
		else {
			// Should never happen...
			throw new IllegalStateException(
					"SyncInvocableHandlerMethod should have completed synchronously.");
		}
	}

	public static void main(String[] args) {
		SpringApplication.run(GatewaySampleApplication.class, args);
	}
}
