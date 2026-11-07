/*
 * Copyright 2013-present the original author or authors.
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

package org.springframework.cloud.gateway.tests.httpclient;

import java.net.URI;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.cloud.gateway.server.mvc.common.MvcUtils;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.cloud.gateway.server.mvc.filter.BeforeFilterFunctions.rewritePath;
import static org.springframework.cloud.gateway.server.mvc.handler.GatewayRouterFunctions.route;
import static org.springframework.cloud.gateway.server.mvc.handler.HandlerFunctions.http;

/**
 * A cookie set on one proxied response must not be sent with later requests.
 */
public class CookieHandlingTests {

	@ParameterizedTest
	@ValueSource(strings = { "http-components", "reactor", "jdk", "simple" })
	void proxyDoesNotSendCookiesFromEarlierResponses(String factory) {
		try (ConfigurableApplicationContext context = new SpringApplicationBuilder(CookieApplication.class)
			.properties("server.port=0", "spring.http.clients.imperative.factory=" + factory)
			.run()) {
			String port = context.getEnvironment().getProperty("local.server.port");
			// the JDK client doesn't keep cookies, so only the proxy's client could add
			// one
			RestClient client = RestClient.builder()
				.requestFactory(new JdkClientHttpRequestFactory())
				.baseUrl("http://localhost:" + port)
				.build();

			client.get().uri("/proxy/set-cookie").retrieve().toBodilessEntity();
			String cookie = client.get().uri("/proxy/cookie").retrieve().body(String.class);

			assertThat(cookie).isEqualTo("none");
		}
	}

	@SpringBootConfiguration
	@EnableAutoConfiguration
	static class CookieApplication {

		@Bean
		RouterFunction<ServerResponse> proxyRoute() {
			return route("proxy").GET("/proxy/**", http()).before(request -> {
				String port = MvcUtils.getApplicationContext(request).getEnvironment().getProperty("local.server.port");
				MvcUtils.setRequestUrl(request, URI.create("http://localhost:" + port));
				return request;
			}).before(rewritePath("/proxy/(?<path>.*)", "/backend/${path}")).build();
		}

		@Bean
		BackendController backendController() {
			return new BackendController();
		}

	}

	@RestController
	static class BackendController {

		@GetMapping("/backend/set-cookie")
		ResponseEntity<Void> setCookie() {
			return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE, "session=abc123; Path=/").build();
		}

		@GetMapping("/backend/cookie")
		String cookie(@RequestHeader(value = HttpHeaders.COOKIE, required = false) String cookie) {
			return (cookie != null) ? cookie : "none";
		}

	}

}
