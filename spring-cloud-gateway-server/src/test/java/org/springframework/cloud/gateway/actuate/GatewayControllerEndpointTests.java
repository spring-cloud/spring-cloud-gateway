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
import java.util.UUID;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.assertj.core.util.Maps;
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
	public void testEndpoints() {
		testClient.get()
			.uri("http://localhost:" + port + "/actuator/gateway")
			.exchange()
			.expectStatus()
			.isOk()
			.expectBodyList(Map.class)
			.consumeWith(result -> {
				List<Map> responseBody = result.getResponseBody();
				assertThat(responseBody).isNotEmpty();
				assertThat(responseBody).contains(Map.of("href", "/actuator/gateway/", "methods", List.of("GET")),
						Map.of("href", "/actuator/gateway/globalfilters", "methods", List.of("GET")),
						Map.of("href", "/actuator/gateway/refresh", "methods", List.of("POST")),
						Map.of("href", "/actuator/gateway/routedefinitions", "methods", List.of("GET")),
						Map.of("href", "/actuator/gateway/routefilters", "methods", List.of("GET")),
						Map.of("href", "/actuator/gateway/routepredicates", "methods", List.of("GET")),
						Map.of("href", "/actuator/gateway/routes", "methods", List.of("POST", "GET")),
						Map.of("href", "/actuator/gateway/routes/test-service", "methods",
								List.of("POST", "DELETE", "GET")),
						Map.of("href", "/actuator/gateway/routes/route_with_metadata", "methods",
								List.of("POST", "DELETE", "GET")));
			});
	}

	@Test
	public void testRefresh() {
		testClient.post()
			.uri("http://localhost:" + port + "/actuator/gateway/refresh")
			.exchange()
			.expectStatus()
			.isOk();
	}

	@Test
	public void testRoutes() {
		testClient.get()
			.uri("http://localhost:" + port + "/actuator/gateway/routes")
			.exchange()
			.expectStatus()
			.isOk()
			.expectBodyList(Map.class)
			.consumeWith(result -> {
				List<Map> responseBody = result.getResponseBody();
				assertThat(responseBody).isNotEmpty();
			});
	}

	@Test
	public void testGetSpecificRoute() {
		testClient.get()
			.uri("http://localhost:" + port + "/actuator/gateway/routes/test-service")
			.exchange()
			.expectStatus()
			.isOk()
			.expectBodyList(Map.class)
			.consumeWith(result -> {
				List<Map> responseBody = result.getResponseBody();
				assertThat(responseBody).isNotNull();
				assertThat(responseBody.size()).isEqualTo(1);
				assertThat(responseBody).isNotEmpty();
			});
	}

	@Test
	public void testRouteReturnsMetadata() {
		testClient.get()
			.uri("http://localhost:" + port + "/actuator/gateway/routes/route_with_metadata")
			.exchange()
			.expectStatus()
			.isOk()
			.expectBody()
			.jsonPath("$.metadata")
			.value(map -> assertThat((Map<String, Object>) map).hasSize(3)
				.containsEntry("optionName", "OptionValue")
				.containsEntry("iAmNumber", 1)
				.containsEntry("compositeObject", Maps.newHashMap("name", "value")));
	}

	@Test
	public void testRouteFilters() {
		testClient.get()
			.uri("http://localhost:" + port + "/actuator/gateway/routefilters")
			.exchange()
			.expectStatus()
			.isOk()
			.expectBody(Map.class)
			.consumeWith(result -> {
				Map<?, ?> responseBody = result.getResponseBody();
				assertThat(responseBody).isNotEmpty();
			});
	}

	@Test
	public void testRoutePredicates() {
		testClient.get()
			.uri("http://localhost:" + port + "/actuator/gateway/routepredicates")
			.exchange()
			.expectStatus()
			.isOk()
			.expectBody(Map.class)
			.consumeWith(result -> {
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

		testClient.post()
			.uri("http://localhost:" + port + "/actuator/gateway/routes/test-route-to-be-delete")
			.accept(MediaType.APPLICATION_JSON)
			.body(BodyInserters.fromValue(testRouteDefinition))
			.exchange()
			.expectStatus()
			.isCreated();

		testClient.delete()
			.uri("http://localhost:" + port + "/actuator/gateway/routes/test-route-to-be-delete")
			.exchange()
			.expectStatus()
			.isOk()
			.expectBody(ResponseEntity.class)
			.consumeWith(result -> {
				HttpStatusCode httpStatus = result.getStatus();
				assertThat(HttpStatus.OK).isEqualTo(httpStatus);
			});
	}

	@Test
	public void testPostValidRouteDefinition() {
		RouteDefinition testRouteDefinition = new RouteDefinition();
		testRouteDefinition.setUri(URI.create("http://example.org"));

		FilterDefinition prefixPathFilterDefinition = new FilterDefinition("PrefixPath=/test-path");
		FilterDefinition redirectToFilterDefinition = new FilterDefinition("RemoveResponseHeader=Sensitive-Header");
		FilterDefinition testFilterDefinition = new FilterDefinition("TestFilter");
		testRouteDefinition
			.setFilters(Arrays.asList(prefixPathFilterDefinition, redirectToFilterDefinition, testFilterDefinition));

		PredicateDefinition hostRoutePredicateDefinition = new PredicateDefinition("Host=myhost.org");
		PredicateDefinition methodRoutePredicateDefinition = new PredicateDefinition("Method=GET");
		PredicateDefinition testPredicateDefinition = new PredicateDefinition("Test=value");
		testRouteDefinition.setPredicates(
				Arrays.asList(hostRoutePredicateDefinition, methodRoutePredicateDefinition, testPredicateDefinition));

		testClient.post()
			.uri("http://localhost:" + port + "/actuator/gateway/routes/test-route")
			.accept(MediaType.APPLICATION_JSON)
			.body(BodyInserters.fromValue(testRouteDefinition))
			.exchange()
			.expectStatus()
			.isCreated();
	}

	@Test
	public void testRefreshByGroup() {
		RouteDefinition testRouteDefinition = new RouteDefinition();
		testRouteDefinition.setUri(URI.create("http://example.org"));
		String group1 = "group-1_" + UUID.randomUUID();
		testRouteDefinition.setMetadata(Map.of("groupBy", group1));

		String routeId1 = "route-1_" + UUID.randomUUID();
		testClient.post()
			.uri("http://localhost:" + port + "/actuator/gateway/routes/" + routeId1)
			.accept(MediaType.APPLICATION_JSON)
			.body(BodyInserters.fromValue(testRouteDefinition))
			.exchange()
			.expectStatus()
			.isCreated();

		RouteDefinition testRouteDefinition2 = new RouteDefinition();
		testRouteDefinition2.setUri(URI.create("http://example.org"));
		String group2 = "group-2_" + UUID.randomUUID();
		testRouteDefinition2.setMetadata(Map.of("groupBy", group2));
		String routeId2 = "route-2_" + UUID.randomUUID();
		testClient.post()
			.uri("http://localhost:" + port + "/actuator/gateway/routes/" + routeId2)
			.accept(MediaType.APPLICATION_JSON)
			.body(BodyInserters.fromValue(testRouteDefinition2))
			.exchange()
			.expectStatus()
			.isCreated();

		testClient.post()
			.uri("http://localhost:" + port + "/actuator/gateway/refresh?metadata=groupBy:" + group1)
			.exchange()
			.expectStatus()
			.isOk();

		testClient.get()
			.uri("http://localhost:" + port + "/actuator/gateway/routes")
			.exchange()
			.expectStatus()
			.isOk()
			.expectBodyList(Map.class)
			.consumeWith(result -> {
				List<Map> responseBody = result.getResponseBody();
				assertThat(responseBody).extracting("route_id").contains(routeId1).doesNotContain(routeId2);
			});
	}

	@Test
	public void testOrderOfRefreshByGroup() {
		RouteDefinition testRouteDefinition = new RouteDefinition();
		testRouteDefinition.setUri(URI.create("http://example.org"));
		testRouteDefinition.setOrder(1000);
		String group1 = "group-1_" + UUID.randomUUID();
		testRouteDefinition.setMetadata(Map.of("groupBy", group1));

		String routeId1 = "route-1_" + UUID.randomUUID();
		testClient.post()
			.uri("http://localhost:" + port + "/actuator/gateway/routes/" + routeId1)
			.accept(MediaType.APPLICATION_JSON)
			.body(BodyInserters.fromValue(testRouteDefinition))
			.exchange()
			.expectStatus()
			.isCreated();

		RouteDefinition testRouteDefinition2 = new RouteDefinition();
		testRouteDefinition2.setUri(URI.create("http://example.org"));
		testRouteDefinition2.setOrder(0);
		String group2 = "group-2_" + UUID.randomUUID();
		testRouteDefinition2.setMetadata(Map.of("groupBy", group2));
		String routeId2 = "route-2_" + UUID.randomUUID();
		testClient.post()
			.uri("http://localhost:" + port + "/actuator/gateway/routes/" + routeId2)
			.accept(MediaType.APPLICATION_JSON)
			.body(BodyInserters.fromValue(testRouteDefinition2))
			.exchange()
			.expectStatus()
			.isCreated();

		testClient.post()
			.uri("http://localhost:" + port + "/actuator/gateway/refresh?metadata=groupBy:" + group1)
			.exchange()
			.expectStatus()
			.isOk();
		testClient.post()
			.uri("http://localhost:" + port + "/actuator/gateway/refresh?metadata=groupBy:" + group2)
			.exchange()
			.expectStatus()
			.isOk();

		testClient.get()
			.uri("http://localhost:" + port + "/actuator/gateway/routes")
			.exchange()
			.expectStatus()
			.isOk()
			.expectBodyList(Map.class)
			.consumeWith(result -> {
				List<Map> responseBody = result.getResponseBody();

				List ids = responseBody.stream()
					.map(route -> route.get("route_id"))
					.filter(id -> id.equals(routeId1) || id.equals(routeId2))
					.collect(Collectors.toList());
				assertThat(ids).containsExactly(routeId2, routeId1);
			});

		testRouteDefinition2.setOrder(testRouteDefinition.getOrder() + 1);
		testClient.post()
			.uri("http://localhost:" + port + "/actuator/gateway/routes/" + routeId2)
			.accept(MediaType.APPLICATION_JSON)
			.body(BodyInserters.fromValue(testRouteDefinition2))
			.exchange()
			.expectStatus()
			.isCreated();
		testClient.post()
			.uri("http://localhost:" + port + "/actuator/gateway/refresh?metadata=groupBy:" + group2)
			.exchange()
			.expectStatus()
			.isOk();
		testClient.get()
			.uri("http://localhost:" + port + "/actuator/gateway/routes")
			.exchange()
			.expectStatus()
			.isOk()
			.expectBodyList(Map.class)
			.consumeWith(result -> {
				List<Map> responseBody = result.getResponseBody();
				List ids = responseBody.stream()
					.map(route -> route.get("route_id"))
					.filter(id -> id.equals(routeId1) || id.equals(routeId2))
					.collect(Collectors.toList());
				assertThat(ids).containsExactly(routeId1, routeId2);
			});
	}

	@Test
	public void testRefreshByGroup_whenRouteDefinitionsAreDeleted() {
		RouteDefinition testRouteDefinition = new RouteDefinition();
		testRouteDefinition.setUri(URI.create("http://example.org"));
		String group1 = "group-1_" + UUID.randomUUID();
		testRouteDefinition.setMetadata(Map.of("groupBy", group1));

		String routeId1 = "route-1_" + UUID.randomUUID();
		testClient.post()
			.uri("http://localhost:" + port + "/actuator/gateway/routes/" + routeId1)
			.accept(MediaType.APPLICATION_JSON)
			.body(BodyInserters.fromValue(testRouteDefinition))
			.exchange()
			.expectStatus()
			.isCreated();

		testClient.post()
			.uri("http://localhost:" + port + "/actuator/gateway/refresh?metadata=groupBy:" + group1)
			.exchange()
			.expectStatus()
			.isOk();

		testClient.delete()
			.uri("http://localhost:" + port + "/actuator/gateway/routes/" + routeId1)
			.exchange()
			.expectStatus()
			.isOk();

		testClient.post()
			.uri("http://localhost:" + port + "/actuator/gateway/refresh?metadata=groupBy:" + group1)
			.exchange()
			.expectStatus()
			.isOk();

		testClient.get()
			.uri("http://localhost:" + port + "/actuator/gateway/routes")
			.exchange()
			.expectStatus()
			.isOk()
			.expectBodyList(Map.class)
			.consumeWith(result -> {
				List<Map> responseBody = result.getResponseBody();
				assertThat(responseBody).extracting("route_id").doesNotContain(routeId1);
			});
	}

	@Test
	public void testRefreshByGroupWithOneWrongFilterInSameGroup() {
		RouteDefinition testRouteDefinition = new RouteDefinition();
		testRouteDefinition.setUri(URI.create("http://wrong.route"));
		String group1 = "group-1_" + UUID.randomUUID();
		testRouteDefinition.setMetadata(Map.of("groupBy", group1));
		testRouteDefinition.setFilters(List.of(new FilterDefinition("StripPrefix=wrong")));

		String routeId1 = UUID.randomUUID().toString();
		testClient.post()
			.uri("http://localhost:" + port + "/actuator/gateway/routes/" + routeId1)
			.accept(MediaType.APPLICATION_JSON)
			.body(BodyInserters.fromValue(testRouteDefinition))
			.exchange()
			.expectStatus()
			.isCreated();

		RouteDefinition testRouteDefinition2 = new RouteDefinition();
		testRouteDefinition2.setUri(URI.create("http://valid.route"));
		testRouteDefinition2.setMetadata(Map.of("groupBy", group1));
		String routeId2 = UUID.randomUUID().toString();
		testClient.post()
			.uri("http://localhost:" + port + "/actuator/gateway/routes/" + routeId2)
			.accept(MediaType.APPLICATION_JSON)
			.body(BodyInserters.fromValue(testRouteDefinition2))
			.exchange()
			.expectStatus()
			.isCreated();

		testClient.post()
			.uri("http://localhost:" + port + "/actuator/gateway/refresh?metadata=groupBy:" + group1)
			.exchange()
			.expectStatus()
			.isOk();

		testClient.get()
			.uri("http://localhost:" + port + "/actuator/gateway/routes")
			.exchange()
			.expectStatus()
			.isOk()
			.expectBodyList(Map.class)
			.consumeWith(result -> {
				List<Map> responseBody = result.getResponseBody();
				assertThat(responseBody).extracting("route_id").doesNotContain(routeId1, routeId2);
			});
	}

	@Test
	public void testRefreshByGroupDoesntImpactOthers() {
		RouteDefinition testRouteDefinition = new RouteDefinition();
		String routeId1 = UUID.randomUUID().toString();
		testRouteDefinition.setId(routeId1);
		testRouteDefinition.setUri(URI.create("http://wrong-group-1.route"));
		String group1 = "group-1_" + UUID.randomUUID();
		testRouteDefinition.setMetadata(Map.of("groupBy", group1));
		testRouteDefinition.setFilters(List.of(new FilterDefinition("StripPrefix=wrong")));
		RouteDefinition testRouteDefinition2 = new RouteDefinition();
		String routeId2 = UUID.randomUUID().toString();
		testRouteDefinition2.setId(routeId2);
		testRouteDefinition2.setUri(URI.create("http://valid-group-1.route"));
		testRouteDefinition2.setMetadata(Map.of("groupBy", group1));
		testClient.post()
			.uri("http://localhost:" + port + "/actuator/gateway/routes/" + routeId1)
			.accept(MediaType.APPLICATION_JSON)
			.body(BodyInserters.fromValue(testRouteDefinition))
			.exchange()
			.expectStatus()
			.isCreated();
		testClient.post()
			.uri("http://localhost:" + port + "/actuator/gateway/routes/" + routeId2)
			.accept(MediaType.APPLICATION_JSON)
			.body(BodyInserters.fromValue(testRouteDefinition2))
			.exchange()
			.expectStatus()
			.isCreated();

		RouteDefinition testRouteDefinition3 = new RouteDefinition();
		String routeId3 = UUID.randomUUID().toString();
		testRouteDefinition3.setId(routeId3);
		testRouteDefinition3.setUri(URI.create("http://valid-group-2.route"));
		String group2 = "group-2_" + UUID.randomUUID();
		testRouteDefinition3.setMetadata(Map.of("groupBy", group2));
		RouteDefinition testRouteDefinition4 = new RouteDefinition();
		String routeId4 = UUID.randomUUID().toString();
		testRouteDefinition4.setId(routeId4);
		testRouteDefinition4.setUri(URI.create("http://valid-group-2.route"));
		testRouteDefinition4.setMetadata(Map.of("groupBy", group2));
		testClient.post()
			.uri("http://localhost:" + port + "/actuator/gateway/routes/" + routeId3)
			.accept(MediaType.APPLICATION_JSON)
			.body(BodyInserters.fromValue(testRouteDefinition3))
			.exchange()
			.expectStatus()
			.isCreated();
		testClient.post()
			.uri("http://localhost:" + port + "/actuator/gateway/routes/" + routeId4)
			.accept(MediaType.APPLICATION_JSON)
			.body(BodyInserters.fromValue(testRouteDefinition4))
			.exchange()
			.expectStatus()
			.isCreated();

		// When
		testClient.post()
			.uri("http://localhost:" + port + "/actuator/gateway/refresh?metadata=groupBy:" + group1)
			.exchange()
			.expectStatus()
			.isOk();
		testClient.post()
			.uri("http://localhost:" + port + "/actuator/gateway/refresh?metadata=groupBy:" + group2)
			.exchange()
			.expectStatus()
			.isOk();

		// Then
		testClient.get()
			.uri("http://localhost:" + port + "/actuator/gateway/routes")
			.exchange()
			.expectStatus()
			.isOk()
			.expectBodyList(Map.class)
			.consumeWith(result -> {
				List<Map> responseBody = result.getResponseBody();
				assertThat(responseBody).extracting("route_id")
					.doesNotContain(routeId1, routeId2)
					.contains(routeId3, routeId4);

			});
	}

	@Test
	public void testPostMultipleValidRouteDefinitions() {
		RouteDefinition testRouteDefinition = new RouteDefinition();
		testRouteDefinition.setUri(URI.create("http://example.org"));
		String routeId1 = UUID.randomUUID().toString();
		testRouteDefinition.setId(routeId1);

		FilterDefinition prefixPathFilterDefinition = new FilterDefinition("PrefixPath=/test-path");
		FilterDefinition redirectToFilterDefinition = new FilterDefinition("RemoveResponseHeader=Sensitive-Header");
		FilterDefinition testFilterDefinition = new FilterDefinition("TestFilter");
		testRouteDefinition
			.setFilters(Arrays.asList(prefixPathFilterDefinition, redirectToFilterDefinition, testFilterDefinition));

		PredicateDefinition hostRoutePredicateDefinition = new PredicateDefinition("Host=myhost.org");
		PredicateDefinition methodRoutePredicateDefinition = new PredicateDefinition("Method=GET");
		PredicateDefinition testPredicateDefinition = new PredicateDefinition("Test=value");
		testRouteDefinition.setPredicates(
				Arrays.asList(hostRoutePredicateDefinition, methodRoutePredicateDefinition, testPredicateDefinition));

		RouteDefinition testRouteDefinition2 = new RouteDefinition();
		testRouteDefinition2.setUri(URI.create("http://example-2.org"));
		String routeId2 = UUID.randomUUID().toString();
		testRouteDefinition2.setId(routeId2);

		FilterDefinition prefixPathFilterDefinition2 = new FilterDefinition("PrefixPath=/test-path-2");
		FilterDefinition redirectToFilterDefinition2 = new FilterDefinition("RemoveResponseHeader=Sensitive-Header-2");
		FilterDefinition testFilterDefinition2 = new FilterDefinition("TestFilter");
		testRouteDefinition2
			.setFilters(Arrays.asList(prefixPathFilterDefinition2, redirectToFilterDefinition2, testFilterDefinition2));

		PredicateDefinition hostRoutePredicateDefinition2 = new PredicateDefinition("Host=myhost-2.org");
		PredicateDefinition methodRoutePredicateDefinition2 = new PredicateDefinition("Method=GET");
		PredicateDefinition testPredicateDefinition2 = new PredicateDefinition("Test=value-2");
		testRouteDefinition2.setPredicates(Arrays.asList(hostRoutePredicateDefinition2, methodRoutePredicateDefinition2,
				testPredicateDefinition2));

		List<RouteDefinition> multipleRouteDefs = List.of(testRouteDefinition, testRouteDefinition2);

		testClient.post()
			.uri("http://localhost:" + port + "/actuator/gateway/routes")
			.accept(MediaType.APPLICATION_JSON)
			.body(BodyInserters.fromValue(multipleRouteDefs))
			.exchange()
			.expectStatus()
			.isOk();
		testClient.get()
			.uri("http://localhost:" + port + "/actuator/gateway/routedefinitions")
			.accept(MediaType.APPLICATION_JSON)
			.exchange()
			.expectBody()
			.jsonPath("[?(@.id in ['%s','%s'])].id".formatted(routeId1, routeId2))
			.exists();
	}

	@Test
	public void testPostMultipleRoutesWithOneWrong_doesntPersistRouteDefinitions() {

		RouteDefinition testRouteDefinition = new RouteDefinition();
		testRouteDefinition.setUri(URI.create("http://example.org"));
		String routeId1 = UUID.randomUUID().toString();
		testRouteDefinition.setId(routeId1);

		FilterDefinition prefixPathFilterDefinition = new FilterDefinition("PrefixPath=/test-path");
		FilterDefinition redirectToFilterDefinition = new FilterDefinition("RemoveResponseHeader=Sensitive-Header");
		FilterDefinition testFilterDefinition = new FilterDefinition("TestFilter");
		testRouteDefinition
			.setFilters(Arrays.asList(prefixPathFilterDefinition, redirectToFilterDefinition, testFilterDefinition));

		PredicateDefinition hostRoutePredicateDefinition = new PredicateDefinition("Host=myhost.org");
		PredicateDefinition methodRoutePredicateDefinition = new PredicateDefinition("Method=GET");
		PredicateDefinition testPredicateDefinition = new PredicateDefinition("Test=value");
		testRouteDefinition.setPredicates(
				Arrays.asList(hostRoutePredicateDefinition, methodRoutePredicateDefinition, testPredicateDefinition));

		RouteDefinition testRouteDefinition2 = new RouteDefinition();
		testRouteDefinition2.setUri(URI.create("this-is-wrong"));
		String routeId2 = UUID.randomUUID().toString();
		testRouteDefinition2.setId(routeId2);

		List<RouteDefinition> multipleRouteDefs = List.of(testRouteDefinition, testRouteDefinition2);

		testClient.post()
			.uri("http://localhost:" + port + "/actuator/gateway/routes")
			.accept(MediaType.APPLICATION_JSON)
			.body(BodyInserters.fromValue(multipleRouteDefs))
			.exchange()
			.expectStatus()
			.is4xxClientError();

		testClient.get()
			.uri("http://localhost:" + port + "/actuator/gateway/routedefinitions")
			.accept(MediaType.APPLICATION_JSON)
			.exchange()
			.expectBody()
			.jsonPath("[?(@.id in ['%s','%s'])].id".formatted(routeId1, routeId2))
			.doesNotExist();
	}

	@Test
	public void testPostValidShortcutRouteDefinition() {
		RouteDefinition testRouteDefinition = new RouteDefinition();
		testRouteDefinition
			.setId("gatewaywithgrpcfiltertest-0-104014-8916263311295787431172436062-test-gateway-tls-client-mapping-0");
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

		testClient.post()
			.uri("http://localhost:" + port + "/actuator/gateway/routes/test-route")
			.accept(MediaType.APPLICATION_JSON)
			.body(BodyInserters.fromValue(testRouteDefinition))
			.exchange()
			.expectStatus()
			.isCreated();
	}

	@Test
	public void testPostRouteWithNotExistingFilter() {

		RouteDefinition testRouteDefinition = new RouteDefinition();
		testRouteDefinition.setUri(URI.create("http://example.org"));

		FilterDefinition filterDefinition = new FilterDefinition("NotExistingFilter=test-config");
		testRouteDefinition.setFilters(Collections.singletonList(filterDefinition));

		testClient.post()
			.uri("http://localhost:" + port + "/actuator/gateway/routes/test-route")
			.accept(MediaType.APPLICATION_JSON)
			.body(BodyInserters.fromValue(testRouteDefinition))
			.exchange()
			.expectStatus()
			.isBadRequest()
			.expectBody()
			.jsonPath("$.message")
			.isEqualTo("Invalid FilterDefinition: [NotExistingFilter]");
	}

	@Test
	public void testPostRouteWithUriWithoutScheme() {

		RouteDefinition testRouteDefinition = new RouteDefinition();
		testRouteDefinition.setUri(URI.create("example.org"));

		testClient.post()
			.uri("http://localhost:" + port + "/actuator/gateway/routes/no-scheme-test-route")
			.accept(MediaType.APPLICATION_JSON)
			.body(BodyInserters.fromValue(testRouteDefinition))
			.exchange()
			.expectStatus()
			.isBadRequest()
			.expectBody()
			.jsonPath("$.message")
			.isEqualTo("The URI format [example.org] is incorrect, scheme can not be empty");
	}

	@Test
	public void testPostRouteWithUri() {

		RouteDefinition testRouteDefinition = new RouteDefinition();
		testRouteDefinition.setUri(null);

		testClient.post()
			.uri("http://localhost:" + port + "/actuator/gateway/routes/no-scheme-test-route")
			.accept(MediaType.APPLICATION_JSON)
			.body(BodyInserters.fromValue(testRouteDefinition))
			.exchange()
			.expectStatus()
			.isBadRequest()
			.expectBody()
			.jsonPath("$.message")
			.isEqualTo("The URI can not be empty");
	}

	@Test
	public void testPostRouteWithNotExistingPredicate() {

		RouteDefinition testRouteDefinition = new RouteDefinition();
		testRouteDefinition.setUri(URI.create("http://example.org"));

		PredicateDefinition predicateDefinition = new PredicateDefinition("NotExistingPredicate=test-config");
		testRouteDefinition.setPredicates(Collections.singletonList(predicateDefinition));

		testClient.post()
			.uri("http://localhost:" + port + "/actuator/gateway/routes/test-route")
			.accept(MediaType.APPLICATION_JSON)
			.body(BodyInserters.fromValue(testRouteDefinition))
			.exchange()
			.expectStatus()
			.isBadRequest()
			.expectBody()
			.jsonPath("$.message")
			.isEqualTo("Invalid PredicateDefinition: [NotExistingPredicate]");
	}

	@SpringBootConfiguration
	@EnableAutoConfiguration
	@Import(PermitAllSecurityConfiguration.class)
	static class TestConfig {

		@Bean
		RouteLocator testRouteLocator(RouteLocatorBuilder routeLocatorBuilder) {
			return routeLocatorBuilder.routes()
				.route("test-service", r -> r.path("/test-service/**").uri("lb://test-service"))
				.build();
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
