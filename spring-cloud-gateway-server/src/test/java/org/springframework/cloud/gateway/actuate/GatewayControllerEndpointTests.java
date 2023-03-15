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

package org.springframework.cloud.gateway.actuate;

import java.net.URI;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import org.assertj.core.util.Maps;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.cloud.gateway.filter.FilterDefinition;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.cloud.gateway.handler.predicate.AbstractRoutePredicateFactory;
import org.springframework.cloud.gateway.handler.predicate.GatewayPredicate;
import org.springframework.cloud.gateway.handler.predicate.PredicateDefinition;
import org.springframework.cloud.gateway.route.RouteDefinition;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.cloud.gateway.test.PermitAllSecurityConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.server.ServerWebExchange;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

@SpringBootTest(properties = { "management.endpoint.gateway.enabled=true",
		"management.endpoints.web.exposure.include=*", "spring.cloud.gateway.actuator.verbose.enabled=true" },
		webEnvironment = RANDOM_PORT)
public class GatewayControllerEndpointTests {

	@Autowired
	WebTestClient testClient;

	@LocalServerPort
	int port;

	@Test
	public void testRefresh() {
		testClient.post().uri("http://localhost:" + port + "/actuator/gateway/refresh").exchange().expectStatus()
				.isOk();
	}

	@Test
	public void testRoutes() {
		testClient.get().uri("http://localhost:" + port + "/actuator/gateway/routes").exchange().expectStatus().isOk()
				.expectBodyList(Map.class).consumeWith(result -> {
					List<Map> responseBody = result.getResponseBody();
					assertThat(responseBody).isNotEmpty();
				});
	}

	@Test
	public void testGetSpecificRoute() {
		testClient.get().uri("http://localhost:" + port + "/actuator/gateway/routes/test-service").exchange()
				.expectStatus().isOk().expectBodyList(Map.class).consumeWith(result -> {
					List<Map> responseBody = result.getResponseBody();
					assertThat(responseBody).isNotNull();
					assertThat(responseBody.size()).isEqualTo(1);
					assertThat(responseBody).isNotEmpty();
				});
	}

	@Test
	public void testRouteReturnsMetadata() {
		testClient.get().uri("http://localhost:" + port + "/actuator/gateway/routes/route_with_metadata").exchange()
				.expectStatus().isOk().expectBody().jsonPath("$.metadata")
				.value(map -> assertThat((Map<String, Object>) map).hasSize(3)
						.containsEntry("optionName", "OptionValue").containsEntry("iAmNumber", 1)
						.containsEntry("compositeObject", Maps.newHashMap("name", "value")));
	}

	@Test
	public void testRouteFilters() {
		testClient.get().uri("http://localhost:" + port + "/actuator/gateway/routefilters").exchange().expectStatus()
				.isOk().expectBody(Map.class).consumeWith(result -> {
					Map<?, ?> responseBody = result.getResponseBody();
					assertThat(responseBody).isNotEmpty();
				});
	}

	@Test
	public void testRoutePredicates() {
		testClient.get().uri("http://localhost:" + port + "/actuator/gateway/routepredicates").exchange().expectStatus()
				.isOk().expectBody(Map.class).consumeWith(result -> {
					Map<?, ?> responseBody = result.getResponseBody();
					assertThat(responseBody).isNotEmpty();
				});
	}

	@Test
	public void testRouteDelete() {
		RouteDefinition testRouteDefinition = new RouteDefinition();
		testRouteDefinition.setUri(URI.create("http://example.org"));

		PredicateDefinition methodRoutePredicateDefinition = new PredicateDefinition("Method=GET");

		testRouteDefinition.setPredicates(Arrays.asList(methodRoutePredicateDefinition));

		testClient.post().uri("http://localhost:" + port + "/actuator/gateway/routes/test-route-to-be-delete")
				.accept(MediaType.APPLICATION_JSON).body(BodyInserters.fromValue(testRouteDefinition)).exchange()
				.expectStatus().isCreated();

		testClient.delete().uri("http://localhost:" + port + "/actuator/gateway/routes/test-route-to-be-delete")
				.exchange().expectStatus().isOk().expectBody(ResponseEntity.class).consumeWith(result -> {
					HttpStatusCode httpStatus = result.getStatus();
					Assertions.assertEquals(HttpStatus.OK, httpStatus);
				});
	}

	@Test
	public void testPostValidRouteDefinition() {

		RouteDefinition testRouteDefinition = new RouteDefinition();
		testRouteDefinition.setUri(URI.create("http://example.org"));

		FilterDefinition prefixPathFilterDefinition = new FilterDefinition("PrefixPath=/test-path");
		FilterDefinition redirectToFilterDefinition = new FilterDefinition("RemoveResponseHeader=Sensitive-Header");
		FilterDefinition testFilterDefinition = new FilterDefinition("TestFilter");
		testRouteDefinition.setFilters(
				Arrays.asList(prefixPathFilterDefinition, redirectToFilterDefinition, testFilterDefinition));

		PredicateDefinition hostRoutePredicateDefinition = new PredicateDefinition("Host=myhost.org");
		PredicateDefinition methodRoutePredicateDefinition = new PredicateDefinition("Method=GET");
		PredicateDefinition testPredicateDefinition = new PredicateDefinition("Test=value");
		testRouteDefinition.setPredicates(
				Arrays.asList(hostRoutePredicateDefinition, methodRoutePredicateDefinition, testPredicateDefinition));

		testClient.post().uri("http://localhost:" + port + "/actuator/gateway/routes/test-route")
				.accept(MediaType.APPLICATION_JSON).body(BodyInserters.fromValue(testRouteDefinition)).exchange()
				.expectStatus().isCreated();
	}

	@Test
	public void testPostValidShortcutRouteDefinition() {
		RouteDefinition testRouteDefinition = new RouteDefinition();
		testRouteDefinition.setId(
				"gatewaywithgrpcfiltertest-0-104014-8916263311295787431172436062-test-gateway-tls-client-mapping-0");
		testRouteDefinition.setUri(URI.create("https://localhost:8095"));
		testRouteDefinition.setOrder(0);
		testRouteDefinition.setMetadata(Collections.emptyMap());

		FilterDefinition longFilterDefinition = new FilterDefinition();
		FilterDefinition stripPrefix = new FilterDefinition();
		stripPrefix.setName("StripPrefix");
		stripPrefix.addArg("_genkey_0", "1");

		longFilterDefinition.setName("JsonToGrpc");
		longFilterDefinition.addArg("_genkey_0", "file:src/main/proto/hello.pb");
		longFilterDefinition.addArg("_genkey_1", "file:src/main/proto/hello.proto");
		longFilterDefinition.addArg("_genkey_2", "HelloService");
		longFilterDefinition.addArg("_genkey_3", "hello");
		testRouteDefinition.setFilters(Collections.singletonList(longFilterDefinition));

		PredicateDefinition hostRoutePredicateDefinition = new PredicateDefinition();
		hostRoutePredicateDefinition.setName("Path");
		hostRoutePredicateDefinition.addArg("_genkey_0", "/json/hello");
		testRouteDefinition.setPredicates(Arrays.asList(hostRoutePredicateDefinition));

		testClient.post().uri("http://localhost:" + port + "/actuator/gateway/routes/test-route")
				.accept(MediaType.APPLICATION_JSON).body(BodyInserters.fromValue(testRouteDefinition)).exchange()
				.expectStatus().isCreated();
	}

	@Test
	public void testPostRouteWithNotExistingFilter() {

		RouteDefinition testRouteDefinition = new RouteDefinition();
		testRouteDefinition.setUri(URI.create("http://example.org"));

		FilterDefinition filterDefinition = new FilterDefinition("NotExistingFilter=test-config");
		testRouteDefinition.setFilters(Collections.singletonList(filterDefinition));

		testClient.post().uri("http://localhost:" + port + "/actuator/gateway/routes/test-route")
				.accept(MediaType.APPLICATION_JSON).body(BodyInserters.fromValue(testRouteDefinition)).exchange()
				.expectStatus().isBadRequest().expectBody().jsonPath("$.message")
				.isEqualTo("Invalid FilterDefinition: [NotExistingFilter]");
	}

	@Test
	public void testPostRouteWithNotExistingPredicate() {

		RouteDefinition testRouteDefinition = new RouteDefinition();
		testRouteDefinition.setUri(URI.create("http://example.org"));

		PredicateDefinition predicateDefinition = new PredicateDefinition("NotExistingPredicate=test-config");
		testRouteDefinition.setPredicates(Collections.singletonList(predicateDefinition));

		testClient.post().uri("http://localhost:" + port + "/actuator/gateway/routes/test-route")
				.accept(MediaType.APPLICATION_JSON).body(BodyInserters.fromValue(testRouteDefinition)).exchange()
				.expectStatus().isBadRequest().expectBody().jsonPath("$.message")
				.isEqualTo("Invalid PredicateDefinition: [NotExistingPredicate]");
	}

	@SpringBootConfiguration
	@EnableAutoConfiguration
	@Import(PermitAllSecurityConfiguration.class)
	static class TestConfig {

		@Bean
		RouteLocator testRouteLocator(RouteLocatorBuilder routeLocatorBuilder) {
			return routeLocatorBuilder.routes()
					.route("test-service", r -> r.path("/test-service/**").uri("lb://test-service")).build();
		}

		@Bean
		public TestFilterGatewayFilterFactory customGatewayFilterFactory() {
			return new TestFilterGatewayFilterFactory();
		}

		@Bean
		public TestRoutePredicateFactory customGatewayPredicateFactory() {
			return new TestRoutePredicateFactory(Object.class);
		}

	}

	private static class TestFilterGatewayFilterFactory extends AbstractGatewayFilterFactory {

		@Override
		public GatewayFilter apply(Object config) {
			return null;
		}

	}

	private static class TestRoutePredicateFactory extends AbstractRoutePredicateFactory {

		TestRoutePredicateFactory(Class configClass) {
			super(configClass);
		}

		@Override
		public Predicate<ServerWebExchange> apply(Object config) {
			return (GatewayPredicate) serverWebExchange -> true;
		}

	}

}
