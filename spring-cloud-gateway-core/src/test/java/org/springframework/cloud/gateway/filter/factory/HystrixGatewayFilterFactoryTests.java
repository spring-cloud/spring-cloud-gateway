/*
 * Copyright 2013-2017 the original author or authors.
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
 *
 */

package org.springframework.cloud.gateway.filter.factory;

import java.util.Collections;
import java.util.Map;

import com.netflix.loadbalancer.Server;
import com.netflix.loadbalancer.ServerList;
import org.junit.Test;
import org.junit.runner.RunWith;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.cloud.gateway.test.BaseWebClientTests;
import org.springframework.cloud.netflix.ribbon.RibbonClient;
import org.springframework.cloud.netflix.ribbon.StaticServerList;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.util.Assert;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;
import static org.springframework.http.MediaType.TEXT_HTML;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = RANDOM_PORT, properties = "debug=true")
@DirtiesContext
public class HystrixGatewayFilterFactoryTests extends BaseWebClientTests {

	@Test
	public void hystrixFilterWorks() {
		testClient.get().uri("/get")
				.header("Host", "www.hystrixsuccess.org")
				.exchange()
				.expectStatus().isOk()
				.expectHeader().valueEquals(ROUTE_ID_HEADER, "hystrix_success_test");
	}

	@Test
	public void hystrixFilterTimesout() {
		testClient.get().uri("/delay/3")
				.header("Host", "www.hystrixfailure.org")
				.exchange()
				.expectStatus().isEqualTo(HttpStatus.GATEWAY_TIMEOUT)
				.expectBody()
				.jsonPath("$.status")
				.isEqualTo(String.valueOf(HttpStatus.GATEWAY_TIMEOUT.value()));
	}

	/*
	 * Tests that timeouts bubbling from the underpinning WebClient are treated the same as
	 * Hystrix timeouts in terms of outside response. (Internally, timeouts from the WebClient
	 * are seen as command failures and trigger the opening of circuit breakers the same way
	 * timeouts do; it may be confusing in terms of the Hystrix metrics though)
	 */
	@Test
	public void hystrixTimeoutFromWebClient() {
		testClient.get().uri("/delay/10")
				.header("Host", "www.hystrixresponsestall.org")
				.exchange()
				.expectStatus().isEqualTo(HttpStatus.GATEWAY_TIMEOUT);
	}

	@Test
	public void hystrixFilterFallback() {
		testClient.get().uri("/delay/3?a=b")
				.header("Host", "www.hystrixfallback.org")
				.exchange()
				.expectStatus().isOk()
				.expectBody().json("{\"from\":\"fallbackcontroller\"}");
	}

	@Test
	public void hystrixFilterWorksJavaDsl() {
		testClient.get().uri("/get")
				.header("Host", "www.hystrixjava.org")
				.exchange()
				.expectStatus().isOk()
				.expectHeader().valueEquals(ROUTE_ID_HEADER, "hystrix_java");
	}

	@Test
	public void hystrixFilterFallbackJavaDsl() {
		testClient.get().uri("/delay/3")
				.header("Host", "www.hystrixjava.org")
				.exchange()
				.expectStatus().isOk()
				.expectBody().json("{\"from\":\"fallbackcontroller2\"}");
	}

	@Test
	public void hystrixFilterConnectFailure() {
		testClient.get().uri("/delay/3")
				.header("Host", "www.hystrixconnectfail.org")
				.exchange()
				.expectStatus().is5xxServerError();
	}

	@Test
	public void hystrixFilterErrorPage() {
		testClient.get().uri("/delay/3")
				.header("Host", "www.hystrixconnectfail.org")
				.accept(TEXT_HTML)
				.exchange()
				.expectStatus().is5xxServerError()
				.expectBody().consumeWith(res -> {
			final String body = new String(res.getResponseBody(), UTF_8);

			Assert.isTrue(body.contains("<h1>Whitelabel Error Page</h1>"),
					"Cannot find the expected white-label error page title in the response");
			Assert.isTrue(body.contains("(type=Internal Server Error, status=500)"),
					"Cannot find the expected error status report in the response");
		});
	}

	@EnableAutoConfiguration
	@SpringBootConfiguration
	@Import(DefaultTestConfig.class)
	@RestController
	@RibbonClient(name = "badservice", configuration = TestBadRibbonConfig.class)
	public static class TestConfig {

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
			return builder.routes()
					.route("hystrix_java", r -> r.host("**.hystrixjava.org")
							.filters(f -> f.prefixPath("/httpbin")
									.hystrix(config -> config.setFallbackUri("forward:/fallbackcontroller2")))
							.uri(uri))
					.route("hystrix_connection_failure", r -> r.host("**.hystrixconnectfail.org")
							.filters(f -> f.prefixPath("/httpbin")
									.hystrix(config -> {}))
							.uri("lb:badservice"))
					/*
					 * This is a route encapsulated in a hystrix command that is ready to wait
					 * for a response far longer than the underpinning WebClient would.
					 */
					.route("hystrix_response_stall", r -> r.host("**.hystrixresponsestall.org")
							.filters(f -> f.prefixPath("/httpbin")
									.hystrix(config -> config.setName("stalling-command")))
							.uri(uri))
					.build();
		}
	}

	protected static class TestBadRibbonConfig {

		@LocalServerPort
		protected int port = 0;

		@Bean
		public ServerList<Server> ribbonServerList() {
			return new StaticServerList<>(new Server("https", "localhost", this.port));
		}
	}
}
