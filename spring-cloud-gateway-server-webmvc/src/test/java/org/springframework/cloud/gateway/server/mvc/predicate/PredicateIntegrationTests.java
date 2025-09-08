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

package org.springframework.cloud.gateway.server.mvc.predicate;

import java.util.Map;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.cloud.gateway.server.mvc.config.GatewayMvcProperties;
import org.springframework.cloud.gateway.server.mvc.test.HttpbinTestcontainers;
import org.springframework.cloud.gateway.server.mvc.test.HttpbinUriResolver;
import org.springframework.cloud.gateway.server.mvc.test.PermitAllSecurityConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerResponse;

import static org.springframework.cloud.gateway.server.mvc.filter.AfterFilterFunctions.addResponseHeader;
import static org.springframework.cloud.gateway.server.mvc.handler.GatewayRouterFunctions.route;
import static org.springframework.cloud.gateway.server.mvc.handler.HandlerFunctions.http;
import static org.springframework.cloud.gateway.server.mvc.predicate.GatewayRequestPredicates.version;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT,
		properties = { GatewayMvcProperties.PREFIX + ".function.enabled=false" })
@ActiveProfiles("versions")
@ContextConfiguration(initializers = HttpbinTestcontainers.class)
public class PredicateIntegrationTests {

	@Autowired
	RestTestClient testClient;

	@Test
	public void versionHeaderWorks() {
		testClient.mutate()
			.build()
			.get()
			.uri("/anything/version11plus")
			.header("X-API-Version", "1.1.0")
			.exchange()
			.expectStatus()
			.isOk()
			.expectHeader()
			.valueEquals("X-Matched-Version", "1.1+");
		testClient.mutate()
			.build()
			.get()
			.uri("/anything/version11plus")
			.header("X-API-Version", "1.5.0")
			.exchange()
			.expectStatus()
			.isOk()
			.expectHeader()
			.valueEquals("X-Matched-Version", "1.1+");
		testClient.mutate()
			.build()
			.get()
			.uri("/anything/version13")
			.header("X-API-Version", "1.3.0")
			.exchange()
			.expectStatus()
			.isOk()
			.expectHeader()
			.valueEquals("X-Matched-Version", "1.3");
		testClient.mutate()
			.build()
			.get()
			.uri("/anything/version20plus")
			.header("X-API-Version", "2.1.0")
			.exchange()
			.expectStatus()
			.isOk()
			.expectHeader()
			.valueEquals("X-Matched-Version", "2.0+");
	}

	@Test
	public void versionMediaTypeWorks() {
		testClient.mutate()
			.build()
			.get()
			.uri("/anything/version11plus")
			.accept(new MediaType(MediaType.APPLICATION_JSON, Map.of("version", "1.1.0")))
			.exchange()
			.expectStatus()
			.isOk()
			.expectHeader()
			.valueEquals("X-Matched-Version", "1.1+");
	}

	@Test
	public void versionRequestParamWorks() {
		testClient.mutate()
			.build()
			.get()
			.uri("/anything/version11plus?apiVersion=1.1.0")
			.exchange()
			.expectStatus()
			.isOk()
			.expectHeader()
			.valueEquals("X-Matched-Version", "1.1+");
	}

	/*
	 * @Test public void customVersionResolverBeanWorks() { testClient.mutate() .build()
	 * .get() .uri("/anything/version11plus?customApiVersionParam=1.1.0") .exchange()
	 * .expectStatus() .isOk() .expectHeader() .valueEquals("X-Matched-Version", "1.1+");
	 * }
	 */

	@Test
	public void invalidVersionNotFound() {
		testClient.mutate()
			.build()
			.get()
			.uri("/anything/version11plus")
			.header("X-API-Version", "1.0.0")
			.exchange()
			.expectStatus()
			.isNotFound();
	}

	@EnableAutoConfiguration
	@SpringBootConfiguration
	@Import(PermitAllSecurityConfiguration.class)
	static class TestConfig {

		@Bean
		public RouterFunction<ServerResponse> gatewayRouterFunctionsModifyRequestBody() {
			// @formatter:off
			return route("version11plus_dsl")
					.GET("/anything/version11plus", version("1.1+"), http())
					.before(new HttpbinUriResolver())
					.after(addResponseHeader("X-Matched-Version", "1.1+"))
					.build().and(
				route("version20plus_dsl")
					.GET("/anything/version20plus", version("2.0+"), http())
					.before(new HttpbinUriResolver())
						.after(addResponseHeader("X-Matched-Version", "2.0+"))
					.build());
			// @formatter:on
		}

	}

}
