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

import org.springframework.boot.SpringApplication;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.cloud.gateway.EnableGateway;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.Routes;
import org.springframework.context.annotation.Bean;

import static org.springframework.cloud.gateway.filter.factory.WebFilterFactories.addResponseHeader;
import static org.springframework.cloud.gateway.handler.predicate.GatewayRequestPredicates.host;
import static org.springframework.cloud.gateway.handler.predicate.GatewayRequestPredicates.path;
// import static org.springframework.web.reactive.function.server.RequestPredicates.path;

/**
 * @author Spencer Gibb
 */
@SpringBootConfiguration
@EnableAutoConfiguration
@EnableGateway
public class GatewaySampleApplication {

	@Bean
	public RouteLocator customRouteLocator() {
		return Routes.locator()
				.route("test")
					.uri("http://httpbin.org:80")
					.predicate(host("**.abc.org").and(path("/image/png")))
					.addResponseHeader("X-TestHeader", "foobar")
					.and()
				.route("test2")
					.uri("http://httpbin.org:80")
					.predicate(path("/image/webp"))
					.add(addResponseHeader("X-AnotherHeader", "baz"))
					.and()
				.build();
	}

	public static void main(String[] args) {
		SpringApplication.run(GatewaySampleApplication.class, args);
	}
}
