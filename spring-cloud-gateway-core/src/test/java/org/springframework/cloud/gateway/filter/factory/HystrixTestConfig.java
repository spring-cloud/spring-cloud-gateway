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

package org.springframework.cloud.gateway.filter.factory;

import java.util.Collections;
import java.util.Map;

import com.netflix.loadbalancer.Server;
import com.netflix.loadbalancer.ServerList;
import reactor.core.publisher.Mono;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.cloud.gateway.test.BaseWebClientTests;
import org.springframework.cloud.netflix.ribbon.RibbonClient;
import org.springframework.cloud.netflix.ribbon.StaticServerList;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;

import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.HYSTRIX_EXECUTION_EXCEPTION_ATTR;
import static org.springframework.web.reactive.function.server.RequestPredicates.GET;
import static org.springframework.web.reactive.function.server.RouterFunctions.route;

@EnableAutoConfiguration
@SpringBootConfiguration
@Import(BaseWebClientTests.DefaultTestConfig.class)
@RestController
@RibbonClient(name = "badservice", configuration = TestBadRibbonConfig.class)
public class HystrixTestConfig {

	@Value("${test.uri}")
	private String uri;

	@RequestMapping("/fallbackcontroller")
	public Map<String, String> fallbackcontroller(@RequestParam("a") String a) {
		return Collections.singletonMap("from", "fallbackcontroller");
	}

	@RequestMapping("/fallbackcontroller2")
	public Map<String, String> fallbackcontroller2() {
		return Collections.singletonMap("from", "fallbackcontroller2");
	}

	@Bean
	public RouteLocator hystrixRouteLocator(RouteLocatorBuilder builder) {
		return builder.routes().route("hystrix_java", r -> r.host("**.hystrixjava.org")
				.filters(f -> f.prefixPath("/httpbin").hystrix(
						config -> config.setFallbackUri("forward:/fallbackcontroller2")))
				.uri(uri))
				.route("hystrix_connection_failure",
						r -> r.host("**.hystrixconnectfail.org")
								.filters(f -> f.prefixPath("/httpbin").hystrix(config -> {
								})).uri("lb:badservice"))
				/*
				 * This is a route encapsulated in a hystrix command that is ready to wait
				 * for a response far longer than the underpinning WebClient would.
				 */
				.route("hystrix_response_stall",
						r -> r.host("**.hystrixresponsestall.org")
								.filters(f -> f.prefixPath("/httpbin").hystrix(
										config -> config.setName("stalling-command")))
								.uri(uri))
				.build();
	}

	@Bean
	ExceptionFallbackHandler exceptionFallbackHandler() {
		return new ExceptionFallbackHandler();
	}

	@Bean
	RouterFunction<ServerResponse> routerFunction(
			ExceptionFallbackHandler exceptionFallbackHandler) {
		return route(GET("/exceptionFallback"),
				exceptionFallbackHandler::retrieveExceptionInfo);
	}

}

class ExceptionFallbackHandler {

	static final String RETRIEVED_EXCEPTION = "Retrieved-Exception";

	Mono<ServerResponse> retrieveExceptionInfo(ServerRequest serverRequest) {
		String exceptionName = serverRequest.attribute(HYSTRIX_EXECUTION_EXCEPTION_ATTR)
				.map(exception -> exception.getClass().getName()).orElse("");
		return ServerResponse.ok().header(RETRIEVED_EXCEPTION, exceptionName).build();
	}

}

@Configuration
class TestBadRibbonConfig {

	@LocalServerPort
	protected int port = 0;

	@Bean
	public ServerList<Server> ribbonServerList() {
		return new StaticServerList<>(new Server("https", "localhost", this.port));
	}

}
