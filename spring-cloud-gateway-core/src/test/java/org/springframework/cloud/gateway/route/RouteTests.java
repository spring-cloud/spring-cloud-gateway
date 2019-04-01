/*
 * Copyright 2013-2018 the original author or authors.
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

package org.springframework.cloud.gateway.route;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import static org.assertj.core.api.Assertions.assertThat;

public class RouteTests {

	@Rule
	public ExpectedException exception = ExpectedException.none();

	@Test
	public void defeaultHttpPort() {
		Route route = Route.async().id("1")
				.predicate(exchange -> true)
				.uri("http://acme.com")
				.build();

		assertThat(route.getUri()).hasHost("acme.com")
				.hasScheme("http")
				.hasPort(80);
	}

	@Test
	public void defeaultHttpsPort() {
		Route route = Route.async().id("1")
				.predicate(exchange -> true)
				.uri("https://acme.com")
				.build();

		assertThat(route.getUri()).hasHost("acme.com")
				.hasScheme("https")
				.hasPort(443);
	}

	@Test
	public void fullUri() {
		Route route = Route.async().id("1")
				.predicate(exchange -> true)
				.uri("http://acme.com:8080")
				.build();

		assertThat(route.getUri()).hasHost("acme.com")
				.hasScheme("http")
				.hasPort(8080);
	}

	@Test
	public void nullScheme() {
		exception.expect(IllegalArgumentException.class);
		Route.async().id("1")
				.predicate(exchange -> true)
				.uri("/pathonly");
	}
}
