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

import java.util.concurrent.TimeUnit;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.server.RequestPredicates;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;

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
		return builder.routes()
				.route(r -> r.host("**.abc.org").and().path("/image/png")
					.filters(f ->
							f.addResponseHeader("X-TestHeader", "foobar"))
					.uri("http://httpbin.org:80")
				)
				.route(r -> r.path("/image/webp")
					.filters(f ->
							f.addResponseHeader("X-AnotherHeader", "baz"))
					.uri("http://httpbin.org:80")
				)
				.route(r -> r.order(-1)
					.host("**.throttle.org").and().path("/get")
					.filters(f -> f.filter(new ThrottleGatewayFilter()
									.setCapacity(1)
									.setRefillTokens(1)
									.setRefillPeriod(10)
									.setRefillUnit(TimeUnit.SECONDS)))
					.uri("http://httpbin.org:80")
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

	public static void main(String[] args) {
		SpringApplication.run(GatewaySampleApplication.class, args);
	}
}
