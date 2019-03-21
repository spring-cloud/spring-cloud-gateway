/*
 * Copyright 2018 the original author or authors.
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
package org.springframework.cloud.gateway.route.builder;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.junit4.SpringRunner;
import reactor.test.StepVerifier;

import java.net.URI;

/**
 * @author Biju Kunjummen
 */

@RunWith(SpringRunner.class)
@SpringBootTest
public class RouteBuilderTests {

	@Autowired
	private RouteLocatorBuilder routeLocatorBuilder;

	@Test
	public void testASetOfRoutes() {
		RouteLocator routeLocator = this.routeLocatorBuilder.routes()
				.route("test1", r -> r.host("*.somehost.org").and().path("/somepath")
						.filters(f -> f.addRequestHeader("header1", "header-value-1"))
						.uri("http://someuri"))
				.route("test2", r -> r.host("*.somehost2.org")
						.filters(f -> f.addResponseHeader("header-response-1",
								"header-response-1"))
						.uri("https://httpbin.org:9090"))
				.build();

		StepVerifier.create(routeLocator.getRoutes())
				.expectNextMatches(
						r -> r.getId().equals("test1") && r.getFilters().size() == 1
								&& r.getUri().equals(URI.create("http://someuri:80")))
				.expectNextMatches(
						r -> r.getId().equals("test2") && r.getFilters().size() == 1
								&& r.getUri()
										.equals(URI.create("https://httpbin.org:9090")))
				.expectComplete().verify();
	}

	@EnableAutoConfiguration
	@Configuration
	public static class SpringConfig {
	}
}
