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

package org.springframework.cloud.gateway.server.mvc;

import java.util.Locale;
import java.util.Map;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.cloud.gateway.server.mvc.test.HttpbinTestcontainers;
import org.springframework.cloud.gateway.server.mvc.test.HttpbinUriResolver;
import org.springframework.cloud.gateway.server.mvc.test.PermitAllSecurityConfiguration;
import org.springframework.cloud.gateway.server.mvc.test.TestLoadBalancerConfig;
import org.springframework.cloud.loadbalancer.annotation.LoadBalancerClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.RouterFunctions;
import org.springframework.web.servlet.function.ServerResponse;

import static org.springframework.cloud.gateway.server.mvc.filter.BeforeFilterFunctions.modifyRequestBody;
import static org.springframework.cloud.gateway.server.mvc.handler.HandlerFunctions.http;
import static org.springframework.cloud.gateway.server.mvc.predicate.GatewayRequestPredicates.host;

@SuppressWarnings("unchecked")
@SpringBootTest(properties = { "spring.http.client.factory=jdk" }, webEnvironment = WebEnvironment.RANDOM_PORT)
@ContextConfiguration(initializers = HttpbinTestcontainers.class)
public class VanillaRouterFunctionTests {

	@LocalServerPort
	int port;

	@Autowired
	RestTestClient restClient;

	@SuppressWarnings("rawtypes")
	@Test
	public void routerFunctionsRouteWorks() {
		restClient.post()
			.uri("/anything/routerfunctionsroute")
			.header("Host", "www.routerfunctionsroute.org")
			.body("hello")
			.exchange()
			.expectStatus()
			.isOk()
			.expectBody(Map.class)
			.consumeWith(result -> {
				System.out.println();
			});
	}

	@SpringBootConfiguration
	@EnableAutoConfiguration
	@LoadBalancerClient(name = "httpbin", configuration = TestLoadBalancerConfig.Httpbin.class)
	@Import(PermitAllSecurityConfiguration.class)
	protected static class TestConfiguration {

		@Bean
		public RouterFunction<ServerResponse> routerFunctionsRoute() {
			// @formatter:off
			return RouterFunctions.route()
					.POST("/anything/routerfunctionsroute", host("**.routerfunctionsroute.org"), http())
					.before(modifyRequestBody(String.class, String.class, null, (request, s) -> s.toUpperCase(Locale.ROOT)))
					.before(new HttpbinUriResolver())
					.build();
			// @formatter:on
		}

	}

}
