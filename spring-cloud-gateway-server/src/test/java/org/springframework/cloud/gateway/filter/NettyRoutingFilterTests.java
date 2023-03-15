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

package org.springframework.cloud.gateway.filter;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.netty.DisposableServer;
import reactor.netty.http.server.HttpServer;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.cloud.gateway.test.BaseWebClientTests;
import org.springframework.cloud.gateway.test.PermitAllSecurityConfiguration;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.util.TestSocketUtils;
import org.springframework.test.web.reactive.server.WebTestClient;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class NettyRoutingFilterTests extends BaseWebClientTests {

	private static int port;

	@Autowired
	private ApplicationContext context;

	@BeforeAll
	public static void beforeAll() {
		port = TestSocketUtils.findAvailableTcpPort();
	}

	@Test
	@Disabled
	void mockServerWorks() {
		WebTestClient client = WebTestClient.bindToApplicationContext(this.context).build();
		client.get().uri("/mockexample").exchange().expectStatus().value(Matchers.lessThan(500));
	}

	@Test
	// gh-2207
	void testCaseInsensitiveScheme() {
		DisposableServer server = HttpServer.create().port(port).host("127.0.0.1").route(
				routes -> routes.get("/issue", (request, response) -> response.sendString(Mono.just("issue2207"))))
				.bindNow();

		try {
			testClient.get().uri("/issue").exchange().expectStatus().isOk().expectBody()
					.consumeWith(entityExchangeResult -> {
						assertThat(entityExchangeResult).isNotNull();
						assertThat(entityExchangeResult.getResponseBody()).isNotNull();
						String content = new String(entityExchangeResult.getResponseBody());
						assertThat(content).isEqualTo("issue2207");
					});
		}
		finally {
			server.disposeNow();
		}
	}

	@SpringBootConfiguration
	@EnableAutoConfiguration
	@Import(PermitAllSecurityConfiguration.class)
	public static class TestConfig {

		@Bean
		public RouteLocator routes(RouteLocatorBuilder builder) {
			return builder.routes()
					.route(p -> p.path("/mockexample").filters(f -> f.prefixPath("/httpbin")).uri("http://example.com"))
					.route(p -> p.path("/issue").uri("HTTP://127.0.0.1:" + port)).build();
		}

	}

}
