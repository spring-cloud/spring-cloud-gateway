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

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

import org.reactivestreams.Publisher;
import org.springframework.cloud.gateway.filter.factory.rewrite.ModifyResponseBodyGatewayFilterFactory;
import reactor.core.publisher.Mono;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.cloud.gateway.filter.factory.rewrite.HttpMessageWriterResponse;
import org.springframework.cloud.gateway.filter.factory.rewrite.ModifyRequestBodyGatewayFilterFactory;
import org.springframework.cloud.gateway.handler.predicate.AbstractRoutePredicateFactory;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.ResolvableType;
import org.springframework.http.MediaType;
import org.springframework.http.codec.HttpMessageReader;
import org.springframework.http.codec.HttpMessageWriter;
import org.springframework.http.codec.ServerCodecConfigurer;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.server.RequestPredicates;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.server.ServerWebExchange;

import static org.springframework.cloud.gateway.filter.factory.rewrite.RewriteUtils.getHttpMessageReader;
import static org.springframework.cloud.gateway.filter.factory.rewrite.RewriteUtils.getHttpMessageWriter;
import static org.springframework.cloud.gateway.filter.factory.rewrite.RewriteUtils.process;

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
							.filter(modifyRequestBodyGatewayFilterFactory().apply(c ->
									c.setRewriteFunction(String.class, Hello.class,
									(exchange, s) -> {
                                        return new Hello(s.toUpperCase());
                                    })))
					).uri(uri)
				)
                .route("rewrite_request_upper", r -> r.host("*.rewriterequestupper.org")
					.filters(f -> f.addRequestHeader("X-TestHeader", "rewrite_request_upper")
							.filter(modifyRequestBodyGatewayFilterFactory().apply(c ->
									c.setRewriteFunction(String.class, String.class,
									(exchange, s) -> {
                                        return s.toUpperCase();
                                    })))
					).uri(uri)
				)
				.route("rewrite_response_upper", r -> r.host("*.rewriteresponseupper.org")
					.filters(f -> f.addRequestHeader("X-TestHeader", "rewrite_response_upper")
							.filter(modifyResponseBodyGatewayFilterFactory().apply(c ->
									c.setRewriteFunction(String.class, String.class,
									(exchange, s) -> {
                                        return s.toUpperCase();
                                    })))
					).uri(uri)
				)
                .route("rewrite_response_obj", r -> r.host("*.rewriteresponseobj.org")
					.filters(f -> f.addRequestHeader("X-TestHeader", "rewrite_response_obj")
							.filter(modifyResponseBodyGatewayFilterFactory().apply(c ->
									c.setRewriteFunction(Map.class, String.class,
									(exchange, map) -> {
										Object data = map.get("data");
                                        return data.toString();
                                    })))
					).uri(uri)
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
	public ModifyRequestBodyGatewayFilterFactory modifyRequestBodyGatewayFilterFactory() {
		return new ModifyRequestBodyGatewayFilterFactory();
	}

	@Bean
	public ModifyResponseBodyGatewayFilterFactory modifyResponseBodyGatewayFilterFactory() {
		return new ModifyResponseBodyGatewayFilterFactory();
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
		ServerCodecConfigurer codecConfigurer;

		public ReadBodyPredicateFactory() {
			super(Object.class);
		}

		@Override
		@SuppressWarnings("unchecked")
		public Predicate<ServerWebExchange> apply(Object config) {
			return exchange -> {
				MediaType mediaType = exchange.getRequest().getHeaders().getContentType();
				ResolvableType elementType = ResolvableType.forClass(String.class);
				Optional<HttpMessageReader<?>> reader = getHttpMessageReader(codecConfigurer, elementType, mediaType);
				boolean answer = false;
                if (reader.isPresent()) {
					Mono<String> readMono = reader.get().readMono(elementType, exchange.getRequest(), null)
							.cast(String.class);
					answer = process(readMono, peek -> {
						Optional<HttpMessageWriter<?>> writer = getHttpMessageWriter(codecConfigurer, elementType, mediaType);

						if (writer.isPresent()) {
							Publisher publisher = Mono.just(peek);
							HttpMessageWriterResponse fakeResponse = new HttpMessageWriterResponse(exchange.getResponse().bufferFactory());
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

	public static void main(String[] args) {
		SpringApplication.run(GatewaySampleApplication.class, args);
	}
}
