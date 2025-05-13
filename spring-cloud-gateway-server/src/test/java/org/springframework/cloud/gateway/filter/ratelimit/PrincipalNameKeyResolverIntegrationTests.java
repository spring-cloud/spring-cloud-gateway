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

package org.springframework.cloud.gateway.filter.ratelimit;

import java.security.Principal;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.TestSocketUtils;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.DEFINED_PORT;
import static org.springframework.web.reactive.function.client.ExchangeFilterFunctions.basicAuthentication;

@SpringBootTest(webEnvironment = DEFINED_PORT,
		properties = { "spring.security.user.name=user", "spring.security.user.password={noop}password",
				"spring.security.user.roles=USER" })
@ActiveProfiles("principalname")
public class PrincipalNameKeyResolverIntegrationTests {

	@LocalServerPort
	protected int port = 0;

	protected WebTestClient client;

	protected String baseUri;

	@BeforeAll
	public static void beforeClass() {
		System.setProperty("server.port", String.valueOf(TestSocketUtils.findAvailableTcpPort()));
	}

	@AfterAll
	public static void afterClass() {
		System.clearProperty("server.port");
	}

	@BeforeEach
	public void setup() {
		this.baseUri = "http://localhost:" + port;
		this.client = WebTestClient.bindToServer().baseUrl(baseUri).build();
	}

	@Test
	public void keyResolverWorks() {
		this.client.mutate()
			.filter(basicAuthentication("user", "password"))
			.build()
			.get()
			.uri("/myapi/1")
			.exchange()
			.expectStatus()
			.isOk()
			.expectBody()
			.json("{\"user\":\"1\"}");
	}

	@RestController
	@RequestMapping("/downstream")
	@EnableAutoConfiguration
	@SpringBootConfiguration
	protected static class TestConfig {

		@Value("${server.port}")
		private int port;

		@GetMapping("/myapi/{id}")
		public Map<String, String> myapi(@PathVariable String id, Principal principal) {
			return Collections.singletonMap(principal.getName(), id);
		}

		@Bean
		public RouteLocator customRouteLocator(RouteLocatorBuilder builder) {
			return builder.routes()
				.route(r -> r.path("/myapi/**")
					.filters(
							f -> f.requestRateLimiter(c -> c.setRateLimiter(myRateLimiter())).prefixPath("/downstream"))
					.uri("http://localhost:" + port))
				.build();
		}

		@Bean
		@Primary
		MyRateLimiter myRateLimiter() {
			return new MyRateLimiter();
		}

		@Bean
		SecurityWebFilterChain springWebFilterChain(ServerHttpSecurity http) {
			return http.httpBasic()
				.and()
				.authorizeExchange()
				.pathMatchers("/myapi/**")
				.authenticated()
				.anyExchange()
				.permitAll()
				.and()
				.build();
		}

		class MyRateLimiter implements RateLimiter<Object> {

			private HashMap<String, Object> map = new HashMap<>();

			@Override
			public Mono<Response> isAllowed(String routeId, String id) {
				return Mono.just(new RateLimiter.Response(true, Collections.singletonMap("X-Value", "5000000")));
			}

			@Override
			public Class<Object> getConfigClass() {
				return Object.class;
			}

			@Override
			public Map<String, Object> getConfig() {
				return map;
			}

			@Override
			public Object newConfig() {
				return null;
			}

		}

	}

}
