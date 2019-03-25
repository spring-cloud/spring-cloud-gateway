/*
 * Copyright 2013-2019 the original author or authors.
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

package org.springframework.cloud.gateway.handler.predicate;

import org.junit.Test;
import org.junit.runner.RunWith;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.handler.RoutePredicateHandlerMapping;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.cloud.gateway.test.BaseWebClientTests;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.junit4.SpringRunner;

import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = RANDOM_PORT)
@DirtiesContext
public class PathRoutePredicateFactoryTests extends BaseWebClientTests {

	@Test
	public void pathRouteWorks() {
		expectPathRoute("/abc/123/function", "www.path.org", "path_test");
	}

	@Test
	public void trailingSlashReturns404() {
		// since the configuration does not allow the trailing / to match this should fail
		testClient.get().uri("/abc/123/function/")
				.header(HttpHeaders.HOST, "www.path.org").exchange().expectStatus()
				.isNotFound();
	}

	@Test
	public void defaultPathRouteWorks() {
		expectPathRoute("/get", "www.thispathshouldnotmatch.org",
				"default_path_to_httpbin");
	}

	private void expectPathRoute(String uri, String host, String routeId) {
		testClient.get().uri(uri).header(HttpHeaders.HOST, host).exchange().expectStatus()
				.isOk().expectHeader()
				.valueEquals(HANDLER_MAPPER_HEADER,
						RoutePredicateHandlerMapping.class.getSimpleName())
				.expectHeader().valueEquals(ROUTE_ID_HEADER, routeId);
	}

	@Test
	public void mulitPathRouteWorks() {
		expectPathRoute("/anything/multi11", "www.pathmulti.org", "path_multi");
		expectPathRoute("/anything/multi22", "www.pathmulti.org", "path_multi");
		expectPathRoute("/anything/multi33", "www.pathmulti.org",
				"default_path_to_httpbin");
	}

	@Test
	public void mulitPathDslRouteWorks() {
		expectPathRoute("/anything/multidsl1", "www.pathmultidsl.org", "path_multi_dsl");
		expectPathRoute("/anything/multidsl2", "www.pathmultidsl.org",
				"default_path_to_httpbin");
		expectPathRoute("/anything/multidsl3", "www.pathmultidsl.org", "path_multi_dsl");
	}

	@Test
	public void pathRouteWorksWithPercent() {
		testClient.get().uri("/abc/123%/function")
				.header(HttpHeaders.HOST, "www.path.org").exchange().expectStatus().isOk()
				.expectHeader()
				.valueEquals(HANDLER_MAPPER_HEADER,
						RoutePredicateHandlerMapping.class.getSimpleName())
				.expectHeader().valueEquals(ROUTE_ID_HEADER, "path_test");
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
					.route("path_multi_dsl", r -> r.host("**.pathmultidsl.org").and()
							.path(false, "/anything/multidsl1", "/anything/multidsl3")
							.filters(f -> f.prefixPath("/httpbin")).uri(uri))
					.build();
		}

	}

}
