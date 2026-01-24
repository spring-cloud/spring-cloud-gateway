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

package org.springframework.cloud.gateway.server.mvc.filter.global;

import java.net.URI;
import java.util.Map;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.cloud.gateway.server.mvc.common.MvcUtils;
import org.springframework.cloud.gateway.server.mvc.test.HttpbinTestcontainers;
import org.springframework.cloud.gateway.server.mvc.test.PermitAllSecurityConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;
import org.springframework.web.util.UriComponentsBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.cloud.gateway.server.mvc.test.TestUtils.getMap;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@ActiveProfiles("globalfiltertests")
@ContextConfiguration(initializers = HttpbinTestcontainers.class)
@AutoConfigureRestTestClient
class GlobalFilterTests {

	@Autowired
	RestTestClient restClient;



	@Test
	@SuppressWarnings("unchecked")
	void configuredRouteWorks() {
		restClient.get()
			.uri("/anything/listRoute1")
			.exchange()
			.expectStatus()
			.isOk()
			.expectBody(Map.class)
			.consumeWith(res -> {
				Map<String, Object> headers = getMap(res.getResponseBody(), "headers");
				assertThat(headers).containsEntry("X-Test", "listRoute1");
			});
	}

	@SpringBootConfiguration
	@EnableAutoConfiguration
	@Import(PermitAllSecurityConfiguration.class)
	static class Config {

	}

	@TestConfiguration
	static class TestConfig {
		@Bean
		public GlobalRequestFilter globalRequestFilter() {
			return new GlobalRequestFilter() {
				@Override
				public ServerRequest processRequest(ServerRequest request) {
					java.lang.String  routeID = MvcUtils.getAttribute(request, "GatewayServerMvc.gatewayRouteId");
					System.out.println(routeID);
					System.out.println("REQUEST GLOBAL FILTER");

					MvcUtils.addOriginalRequestUrl(request, request.uri());
					String path = request.uri().getPath();
					String newPath = "/test/test/test";

					URI rewrittenUri = UriComponentsBuilder.fromUri(request.uri())
							.replacePath(newPath)
							.encode()
							.build()
							.toUri();

					ServerRequest modified = ServerRequest.from(request).uri(rewrittenUri).build();

					return modified;
				}

				@Override
				public int getOrder() {
					return 0;
				}
			};
		}

		@Bean
		public GlobalResponseFilter globalResponseFilter() {
			return new GlobalResponseFilter() {
				@Override
				public ServerResponse processResponse(ServerRequest serverRequest, ServerResponse serverResponse) {
					Map<String, Object> gatewayAttributes = MvcUtils.getGatewayAttributes(serverRequest);
					System.out.println(gatewayAttributes);
					System.out.println("RESPONSE GLOBAL FILTER");
					return serverResponse;
				}

				@Override
				public int getOrder() {
					return 0;
				}
			};
		}
	}

}
