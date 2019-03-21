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

package org.springframework.cloud.gateway.handler.predicate;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.handler.RoutePredicateHandlerMapping;
import org.springframework.cloud.gateway.test.BaseWebClientTests;
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
		testClient.get().uri("/abc/123/function")
				.header(HttpHeaders.HOST, "www.path.org")
				.exchange()
				.expectStatus().isOk()
				.expectHeader().valueEquals(HANDLER_MAPPER_HEADER, RoutePredicateHandlerMapping.class.getSimpleName())
				.expectHeader().valueEquals(ROUTE_ID_HEADER, "path_test");

		//since the configuration does not allow the trailing / to match this should fail
		testClient.get().uri("/abc/123/function/")
				.header(HttpHeaders.HOST, "www.path.org")
				.exchange()
				.expectStatus().is4xxClientError();
	}

	@Test
	public void defaultPathRouteWorks() {
		testClient.get().uri("/get")
				.exchange()
				.expectStatus().isOk()
				.expectHeader().valueEquals(HANDLER_MAPPER_HEADER, RoutePredicateHandlerMapping.class.getSimpleName())
				.expectHeader().valueEquals(ROUTE_ID_HEADER, "default_path_to_httpbin");
	}

	@Test
	public void pathRouteWorksWithPercent() {
		testClient.get().uri("/abc/123%/function")
				.header(HttpHeaders.HOST, "www.path.org")
				.exchange()
				.expectStatus().isOk()
				.expectHeader().valueEquals(HANDLER_MAPPER_HEADER, RoutePredicateHandlerMapping.class.getSimpleName())
				.expectHeader().valueEquals(ROUTE_ID_HEADER, "path_test");
	}

	@EnableAutoConfiguration
	@SpringBootConfiguration
	@Import(DefaultTestConfig.class)
	public static class TestConfig { }

}
