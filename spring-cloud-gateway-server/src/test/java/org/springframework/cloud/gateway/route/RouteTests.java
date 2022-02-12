/*
 * Copyright 2013-2020 the original author or authors.
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

package org.springframework.cloud.gateway.route;

import java.net.URI;
import java.util.HashMap;

import org.assertj.core.util.Maps;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class RouteTests {

	@Test
	public void defaultHttpPort() {
		Route route = Route.async().id("1").predicate(exchange -> true).uri("http://acme.com").build();

		assertThat(route.getUri()).hasHost("acme.com").hasScheme("http").hasPort(80);
	}

	@Test
	public void defaultHttpsPort() {
		Route route = Route.async().id("1").predicate(exchange -> true).uri("https://acme.com").build();

		assertThat(route.getUri()).hasHost("acme.com").hasScheme("https").hasPort(443);
	}

	@Test
	public void fullUri() {
		Route route = Route.async().id("1").predicate(exchange -> true).uri("http://acme.com:8080").build();

		assertThat(route.getUri()).hasHost("acme.com").hasScheme("http").hasPort(8080);
	}

	@Test
	public void nullScheme() {
		assertThatThrownBy(() -> Route.async().id("1").predicate(exchange -> true).uri("/pathonly"))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	public void defaultMetadataToEmpty() {
		Route route = Route.async().id("1").predicate(exchange -> true).uri("http://acme.com:8080").build();

		assertThat(route.getMetadata()).isEmpty();
	}

	@Test
	public void isAbleToAddMetadata() {
		Route route = Route.async().id("1").predicate(exchange -> true).uri("http://acme.com:8080")
				.metadata(Maps.newHashMap("key", "value")).metadata("key2", "value2").build();

		assertThat(route.getMetadata()).hasSize(2).containsEntry("key", "value").containsEntry("key2", "value2");
	}

	@Test
	public void metadataIsAddedFromDefinition() {
		RouteDefinition definition = new RouteDefinition();
		definition.setId("1");
		definition.setUri(URI.create("http://acme.com:8080"));
		HashMap<String, Object> metadata = new HashMap<>();
		metadata.put("key", "value");
		metadata.put("key2", "value2");
		definition.setMetadata(metadata);
		Route route = Route.async(definition).predicate(exchange -> true).build();

		assertThat(route.getMetadata()).hasSize(2).containsEntry("key", "value").containsEntry("key2", "value2");
	}

}
