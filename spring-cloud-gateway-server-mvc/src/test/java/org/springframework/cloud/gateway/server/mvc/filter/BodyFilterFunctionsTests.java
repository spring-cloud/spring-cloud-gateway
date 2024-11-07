/*
 * Copyright 2013-2024 the original author or authors.
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

package org.springframework.cloud.gateway.server.mvc.filter;

import java.util.Map;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.server.mvc.test.HttpbinTestcontainers;
import org.springframework.cloud.gateway.server.mvc.test.HttpbinUriResolver;
import org.springframework.cloud.gateway.server.mvc.test.TestLoadBalancerConfig;
import org.springframework.cloud.gateway.server.mvc.test.client.TestRestClient;
import org.springframework.cloud.loadbalancer.annotation.LoadBalancerClient;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;
import static org.springframework.cloud.gateway.server.mvc.filter.AfterFilterFunctions.modifyResponseBody;
import static org.springframework.cloud.gateway.server.mvc.handler.GatewayRouterFunctions.route;
import static org.springframework.cloud.gateway.server.mvc.handler.HandlerFunctions.http;
import static org.springframework.cloud.gateway.server.mvc.test.TestUtils.getMap;

@SpringBootTest(webEnvironment = RANDOM_PORT)
@ContextConfiguration(initializers = HttpbinTestcontainers.class)
public class BodyFilterFunctionsTests {

	@Autowired
	TestRestClient restClient;

	@Test
	public void modifyResponseBodySimple() {
		restClient.get()
			.uri("/anything/modifyresponsebodysimple")
			.header("X-Foo", "fooval")
			.exchange()
			.expectStatus()
			.isOk()
			.expectBody(Map.class)
			.consumeWith(res -> {
				Map<String, Object> headers = getMap(res.getResponseBody(), "headers");
				assertThat(headers).containsEntry("X-Foo", "FOOVAL");
			});
	}

	@Test
	public void modifyResponseBodyComplex() {
		restClient.get()
			.uri("/deny")
			.header("X-Foo", "fooval")
			.exchange()
			.expectStatus()
			.isOk()
			// deny returns text/plain
			.expectHeader()
			.contentType(MediaType.APPLICATION_JSON)
			.expectBody(Message.class)
			.consumeWith(res -> {
				assertThat(res.getResponseBody().message()).isNotEmpty();
			});
	}

	@SpringBootConfiguration
	@EnableAutoConfiguration
	@LoadBalancerClient(name = "httpbin", configuration = TestLoadBalancerConfig.Httpbin.class)
	protected static class TestConfiguration {

		@Bean
		public RouterFunction<ServerResponse> gatewayRouterFunctionsModifyResponseBodySimple() {
			// @formatter:off
			return route("modify_response_body_simple")
					.GET("/anything/modifyresponsebodysimple", http())
					.before(new HttpbinUriResolver())
					.after(modifyResponseBody(String.class, String.class, null,
							(request, response, s) -> s.replace("fooval", "FOOVAL")))
					.build();
			// @formatter:on
		}

		@Bean
		public RouterFunction<ServerResponse> gatewayRouterFunctionsModifyResponseBodyComplex() {
			// @formatter:off
			return route("modify_response_body_simple")
					.GET("/deny", http())
					.before(new HttpbinUriResolver())
					.after(modifyResponseBody(String.class, Message.class, MediaType.APPLICATION_JSON_VALUE,
							(request, response, s) -> new Message(s)))
					.build();
			// @formatter:on
		}

	}

	record Message(String message) {

	}

}
