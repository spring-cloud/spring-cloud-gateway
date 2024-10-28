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

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.cloud.gateway.server.mvc.test.HttpbinTestcontainers;
import org.springframework.cloud.gateway.server.mvc.test.HttpbinUriResolver;
import org.springframework.cloud.gateway.server.mvc.test.TestLoadBalancerConfig;
import org.springframework.cloud.gateway.server.mvc.test.client.TestRestClient;
import org.springframework.cloud.loadbalancer.annotation.LoadBalancerClient;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerResponse;

import static org.springframework.cloud.gateway.server.mvc.handler.GatewayRouterFunctions.route;
import static org.springframework.cloud.gateway.server.mvc.handler.HandlerFunctions.http;
import static org.springframework.cloud.gateway.server.mvc.predicate.GatewayRequestPredicates.host;

@SuppressWarnings("unchecked")
@SpringBootTest(properties = { "spring.cloud.gateway.mvc.http-client.type=autodetect" },
		webEnvironment = WebEnvironment.RANDOM_PORT)
@ContextConfiguration(initializers = HttpbinTestcontainers.class)
public class SimpleHttpClientIntegrationTests {

	static {
		// if set type to autodetect above
		System.setProperty("sun.net.http.allowRestrictedHeaders", "true");
	}

	@LocalServerPort
	int port;

	@Autowired
	TestRestClient restClient;

	@Test
	public void simpleHttpClientNotFoundWorks() {
		restClient.get().uri("/status/404").header("Host", "www.notfound.org").exchange().expectStatus().isNotFound();
	}

	@SpringBootConfiguration
	@EnableAutoConfiguration
	@LoadBalancerClient(name = "httpbin", configuration = TestLoadBalancerConfig.Httpbin.class)
	protected static class TestConfiguration {

		@Bean
		public RouterFunction<ServerResponse> gatewayRouterFunctions404() {
			// @formatter:off
			return route("testnotfound")
					.GET("/status/404", host("**.notfound.org"), http())
					.before(new HttpbinUriResolver())
					.build();
			// @formatter:on
		}

	}

}
