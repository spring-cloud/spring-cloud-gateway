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

package org.springframework.cloud.gateway.filter.factory;

import java.util.Collections;
import java.util.Map;

import reactor.core.publisher.Mono;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.cloud.gateway.filter.factory.RetryGatewayFilterFactoryIntegrationTests.TestBadLoadBalancerConfig;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.cloud.gateway.test.BaseWebClientTests;
import org.springframework.cloud.loadbalancer.annotation.LoadBalancerClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.server.ServerWebExchange;

import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.CIRCUITBREAKER_EXECUTION_EXCEPTION_ATTR;
import static org.springframework.web.reactive.function.server.RequestPredicates.GET;
import static org.springframework.web.reactive.function.server.RouterFunctions.route;

/**
 * @author Ryan Baxter
 */
@EnableAutoConfiguration
@SpringBootConfiguration
@Import(BaseWebClientTests.DefaultTestConfig.class)
@RestController
@LoadBalancerClient(name = "badservice", configuration = TestBadLoadBalancerConfig.class)
public class SpringCloudCircuitBreakerTestConfig {

	@Value("${test.uri}")
	private String uri;

	@RequestMapping("/circuitbreakerFallbackController")
	public Map<String, String> fallbackcontroller(@RequestParam("a") String a) {
		return Collections.singletonMap("from", "circuitbreakerfallbackcontroller");
	}

	@RequestMapping("/circuitbreakerFallbackController2")
	public Map<String, String> fallbackcontroller2() {
		return Collections.singletonMap("from", "circuitbreakerfallbackcontroller2");
	}

	@RequestMapping("/circuitbreakerFallbackController3")
	public Map<String, String> fallbackcontroller3() {
		return Collections.singletonMap("from", "circuitbreakerfallbackcontroller3");
	}

	@RequestMapping("/statusCodeFallbackController")
	public Map<String, String> statusCodeFallbackController(ServerWebExchange exchange) {
		return Collections.singletonMap("from", "statusCodeFallbackController");
	}

	@Bean
	public RouteLocator circuitBreakerRouteLocator(RouteLocatorBuilder builder) {
		return builder.routes()
				.route("circuitbreaker_fallback_forward", r -> r.host("**.circuitbreakerforward.org")
						.filters(f -> f.circuitBreaker(config -> config.setFallbackUri("forward:/fallback"))).uri(uri))
				.route("fallback_controller_3",
						r -> r.path("/fallback").filters(f -> f.setPath("/circuitbreakerFallbackController3")).uri(uri))
				.route("circuitbreaker_java",
						r -> r.host("**.circuitbreakerjava.org")
								.filters(f -> f.prefixPath("/httpbin").circuitBreaker(
										config -> config.setFallbackUri("forward:/circuitbreakerFallbackController2")))
								.uri(uri))
				.route("circuitbreaker_connection_failure", r -> r.host("**.circuitbreakerconnectfail.org")
						.filters(f -> f.prefixPath("/httpbin").circuitBreaker(config -> {
						})).uri("lb:badservice"))
				/*
				 * This is a route encapsulated in a circuit breaker that is ready to wait
				 * for a response far longer than the underpinning WebClient would.
				 */
				.route("circuitbreaker_response_stall",
						r -> r.host("**.circuitbreakerresponsestall.org")
								.filters(f -> f.prefixPath("/httpbin")
										.circuitBreaker(config -> config.setName("stalling-command")))
								.uri(uri))
				.build();
	}

	@Bean
	CircuitBreakerExceptionFallbackHandler exceptionFallbackHandler() {
		return new CircuitBreakerExceptionFallbackHandler();
	}

	@Bean
	RouterFunction<ServerResponse> routerFunction(CircuitBreakerExceptionFallbackHandler exceptionFallbackHandler) {
		return route(GET("/circuitbreakerExceptionFallback"), exceptionFallbackHandler::retrieveExceptionInfo);
	}

	private static class CircuitBreakerExceptionFallbackHandler {

		static final String RETRIEVED_EXCEPTION = "Retrieved-Exception";

		Mono<ServerResponse> retrieveExceptionInfo(ServerRequest serverRequest) {
			String exceptionName = serverRequest.attribute(CIRCUITBREAKER_EXECUTION_EXCEPTION_ATTR)
					.map(exception -> exception.getClass().getName()).orElse("");
			return ServerResponse.ok().header(RETRIEVED_EXCEPTION, exceptionName).build();
		}

	}

}
