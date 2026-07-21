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

package org.springframework.cloud.gateway.filter.factory;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.cloud.gateway.test.BaseWebClientTests;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;

import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

/**
 * @author Garvit Joshi
 */
@SpringBootTest(webEnvironment = RANDOM_PORT, properties = "spring.webflux.base-path=/context")
@DirtiesContext
public class StripContextPathGatewayFilterFactoryIntegrationTests extends BaseWebClientTests {

	@Test
	public void stripContextPathBeforeRewritePathWorksWithBasePath() {
		testClient.get()
			.uri("/context/api/get")
			.header("Host", "www.stripcontextpath.org")
			.exchange()
			.expectStatus()
			.isOk()
			.expectHeader()
			.valueEquals(ROUTE_ID_HEADER, "test_strip_context_path");
	}

	@Test
	public void rewritePathWithoutStripContextPathFailsWithBasePath() {
		testClient.get()
			.uri("/context/broken/get")
			.header("Host", "www.stripcontextpathmissing.org")
			.exchange()
			.expectStatus()
			.is5xxServerError();
	}

	@EnableAutoConfiguration
	@SpringBootConfiguration
	@Import(DefaultTestConfig.class)
	public static class TestConfig {

		@Value("${test.uri}")
		String uri;

		@Bean
		public RouteLocator testRouteLocator(RouteLocatorBuilder builder) {
			return builder.routes()
				.route("test_strip_context_path", r -> r.order(-1)
					.host("**.stripcontextpath.org")
					.and()
					.path("/api/**")
					.filters(
							f -> f.stripContextPath().rewritePath("/api/(?<segment>.*)", "/context/httpbin/${segment}"))
					.uri(uri))
				.route("test_missing_strip_context_path",
						r -> r.order(-1)
							.host("**.stripcontextpathmissing.org")
							.and()
							.path("/broken/**")
							.filters(f -> f.rewritePath("/context/broken/(?<segment>.*)", "/${segment}"))
							.uri(uri))
				.build();
		}

	}

}
