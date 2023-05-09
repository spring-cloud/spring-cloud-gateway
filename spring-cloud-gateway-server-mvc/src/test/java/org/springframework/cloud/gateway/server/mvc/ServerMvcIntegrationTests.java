/*
 * Copyright 2013-2023 the original author or authors.
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

package org.springframework.cloud.gateway.server.mvc;

import java.util.Collections;
import java.util.Map;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.cloud.gateway.server.mvc.test.DefaultTestRestClient;
import org.springframework.cloud.gateway.server.mvc.test.HttpBinCompatibleController;
import org.springframework.cloud.gateway.server.mvc.test.LocalHostUriBuilderFactory;
import org.springframework.cloud.gateway.server.mvc.test.LocalServerPortUriResolver;
import org.springframework.cloud.gateway.server.mvc.test.TestRestClient;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.cloud.gateway.server.mvc.FilterFunctions.addRequestHeader;
import static org.springframework.cloud.gateway.server.mvc.FilterFunctions.addResponseHeader;
import static org.springframework.cloud.gateway.server.mvc.FilterFunctions.prefixPath;
import static org.springframework.cloud.gateway.server.mvc.FilterFunctions.setStatus;
import static org.springframework.cloud.gateway.server.mvc.HandlerFunctions.http;
import static org.springframework.web.servlet.function.RequestPredicates.GET;
import static org.springframework.web.servlet.function.RouterFunctions.route;

@SpringBootTest(properties = {}, webEnvironment = WebEnvironment.RANDOM_PORT)
public class ServerMvcIntegrationTests {

	@LocalServerPort
	int port;

	@Autowired
	TestRestTemplate restTemplate;

	@Autowired
	TestRestClient restClient;

	@Test
	public void nonGatewayRouterFunctionWorks() {
		restClient.get().uri("/hello").exchange().expectStatus().isOk().expectBody(String.class).isEqualTo("Hello");
	}

	@Test
	public void getGatewayRouterFunctionWorks() {
		ResponseEntity<Map> response = restTemplate.getForEntity("/get", Map.class);
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		Map<String, Object> map0 = response.getBody();
		assertThat(map0).isNotEmpty().containsKey("headers");
		Map<String, Object> headers0 = (Map<String, Object>) map0.get("headers");
		// TODO: assert headers case insensitive
		assertThat(headers0).containsEntry("x-foo", "Bar");

		restClient.get().uri("/get").exchange().expectStatus().isOk().expectBody(Map.class).consumeWith(res -> {
			Map<String, Object> map = res.getResponseBody();
			assertThat(map).isNotEmpty().containsKey("headers");
			Map<String, Object> headers = (Map<String, Object>) map.get("headers");
			assertThat(headers).containsEntry("x-foo", "Bar");
		});
	}

	@Test
	public void setStatusGatewayRouterFunctionWorks() {
		restClient.get().uri("/status/201").exchange().expectStatus().isEqualTo(HttpStatus.TOO_MANY_REQUESTS)
				.expectBody(String.class).isEqualTo("Failed with 201");
	}

	@Test
	public void addResponseHeaderWorks() {
		restClient.get().uri("/anything/addresheader").exchange().expectStatus().isOk().expectBody(Map.class)
				.consumeWith(res -> {
					Map<String, Object> map = res.getResponseBody();
					assertThat(map).isNotEmpty().containsKey("headers");
					Map<String, Object> headers = (Map<String, Object>) map.get("headers");
					assertThat(headers).doesNotContainKey("x-bar");
					assertThat(res.getResponseHeaders()).containsEntry("x-bar", Collections.singletonList("val1"));
				});
	}

	@SpringBootConfiguration
	@EnableAutoConfiguration
	protected static class TestConfiguration {

		// TODO: move to auto config
		@Bean
		public ClientHttpRequestFactory gatewayClientHttpRequestFactory(RestTemplateBuilder builder) {
			return builder.buildRequestFactory();
		}

		@Bean
		public ClientHttpRequestFactoryProxyExchange clientHttpRequestFactoryProxyExchange(
				ClientHttpRequestFactory requestFactory) {
			return new ClientHttpRequestFactoryProxyExchange(requestFactory);
		}

		// TODO: move to auto config
		@Bean
		public DefaultTestRestClient testRestClient(TestRestTemplate testRestTemplate, Environment env) {
			return new DefaultTestRestClient(testRestTemplate, new LocalHostUriBuilderFactory(env), result -> {
			});
		}

		@Bean
		public HttpBinCompatibleController httpBinCompatibleController() {
			return new HttpBinCompatibleController();
		}

		@Bean
		TestHandler testHandler() {
			return new TestHandler();
		}

		@Bean
		public RouterFunction<ServerResponse> nonGatewayRouterFunctions(TestHandler testHandler) {
			return route(GET("/hello"), testHandler::hello);
		}

		@Bean
		public RouterFunction<ServerResponse> gatewayRouterFunctions() {
			return route(GET("/get"), http(new LocalServerPortUriResolver())).filter(addRequestHeader("X-Foo", "Bar"))
					.filter(prefixPath("/httpbin"));
		}

		// TODO: should be about to combine with above
		@Bean
		public RouterFunction<ServerResponse> gatewayRouterFunctions2() {
			return route(GET("/status/{status}"), http(new LocalServerPortUriResolver())).filter(prefixPath("/httpbin"))
					.filter(setStatus(HttpStatus.TOO_MANY_REQUESTS));
		}

		@Bean
		public RouterFunction<ServerResponse> gatewayRouterFunctions3() {
			return route(GET("/anything/addresheader"), http(new LocalServerPortUriResolver()))
					.filter(prefixPath("/httpbin")).filter(addResponseHeader("X-Bar", "val1"));
		}

	}

	protected static class TestHandler {

		public ServerResponse hello(ServerRequest request) {
			return ServerResponse.ok().body("Hello");
		}

	}

}
