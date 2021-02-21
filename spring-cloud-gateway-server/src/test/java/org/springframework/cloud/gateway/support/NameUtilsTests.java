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

package org.springframework.cloud.gateway.support;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import org.springframework.cloud.gateway.filter.AdaptCachedBodyGlobalFilter;
import org.springframework.cloud.gateway.filter.ForwardPathFilter;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.filter.WebsocketRoutingFilter;
import org.springframework.cloud.gateway.filter.factory.AddRequestHeaderGatewayFilterFactory;
import org.springframework.cloud.gateway.filter.factory.DedupeResponseHeaderGatewayFilterFactory;
import org.springframework.cloud.gateway.filter.factory.FallbackHeadersGatewayFilterFactory;
import org.springframework.cloud.gateway.filter.factory.GatewayFilterFactory;
import org.springframework.cloud.gateway.filter.factory.MapRequestHeaderGatewayFilterFactory;
import org.springframework.cloud.gateway.handler.predicate.AfterRoutePredicateFactory;
import org.springframework.cloud.gateway.handler.predicate.CloudFoundryRouteServiceRoutePredicateFactory;
import org.springframework.cloud.gateway.handler.predicate.ReadBodyRoutePredicateFactory;
import org.springframework.cloud.gateway.handler.predicate.RemoteAddrRoutePredicateFactory;
import org.springframework.cloud.gateway.handler.predicate.RoutePredicateFactory;

import static org.assertj.core.api.Assertions.assertThat;

class NameUtilsTests {

	@Test
	void shouldNormalizePredicatesNames() {
		List<Class<? extends RoutePredicateFactory<?>>> predicates = Arrays.asList(AfterRoutePredicateFactory.class,
				CloudFoundryRouteServiceRoutePredicateFactory.class, ReadBodyRoutePredicateFactory.class,
				RemoteAddrRoutePredicateFactory.class);

		List<String> resultNames = predicates.stream().map(NameUtils::normalizeRoutePredicateName)
				.collect(Collectors.toList());

		List<String> expectedNames = Arrays.asList("After", "CloudFoundryRouteService", "ReadBody", "RemoteAddr");

		assertThat(resultNames).isEqualTo(expectedNames);
	}

	@Test
	void shouldNormalizePredicatesNamesAsProperties() {
		List<Class<? extends RoutePredicateFactory<?>>> predicates = Arrays.asList(AfterRoutePredicateFactory.class,
				CloudFoundryRouteServiceRoutePredicateFactory.class, ReadBodyRoutePredicateFactory.class,
				RemoteAddrRoutePredicateFactory.class);

		List<String> resultNames = predicates.stream().map(NameUtils::normalizeRoutePredicateNameAsProperty)
				.collect(Collectors.toList());

		List<String> expectedNames = Arrays.asList("after", "cloud-foundry-route-service", "read-body", "remote-addr");

		assertThat(resultNames).isEqualTo(expectedNames);
	}

	@Test
	void shouldNormalizeFiltersNames() {
		List<Class<? extends GatewayFilterFactory<?>>> predicates = Arrays.asList(
				AddRequestHeaderGatewayFilterFactory.class, DedupeResponseHeaderGatewayFilterFactory.class,
				FallbackHeadersGatewayFilterFactory.class, MapRequestHeaderGatewayFilterFactory.class);

		List<String> resultNames = predicates.stream().map(NameUtils::normalizeFilterFactoryName)
				.collect(Collectors.toList());

		List<String> expectedNames = Arrays.asList("AddRequestHeader", "DedupeResponseHeader", "FallbackHeaders",
				"MapRequestHeader");

		assertThat(resultNames).isEqualTo(expectedNames);
	}

	@Test
	void shouldNormalizeFiltersNamesAsProperties() {
		List<Class<? extends GatewayFilterFactory<?>>> predicates = Arrays.asList(
				AddRequestHeaderGatewayFilterFactory.class, DedupeResponseHeaderGatewayFilterFactory.class,
				FallbackHeadersGatewayFilterFactory.class, MapRequestHeaderGatewayFilterFactory.class);

		List<String> resultNames = predicates.stream().map(NameUtils::normalizeFilterFactoryNameAsProperty)
				.collect(Collectors.toList());

		List<String> expectedNames = Arrays.asList("add-request-header", "dedupe-response-header", "fallback-headers",
				"map-request-header");

		assertThat(resultNames).isEqualTo(expectedNames);
	}

	@Test
	void shouldNormalizeGlobalFiltersNames() {
		List<Class<? extends GlobalFilter>> predicates = Arrays.asList(ForwardPathFilter.class,
				AdaptCachedBodyGlobalFilter.class, WebsocketRoutingFilter.class);

		List<String> resultNames = predicates.stream().map(NameUtils::normalizeGlobalFilterName)
				.collect(Collectors.toList());

		List<String> expectedNames = Arrays.asList("ForwardPath", "AdaptCachedBody", "WebsocketRouting");

		assertThat(resultNames).isEqualTo(expectedNames);
	}

	@Test
	void shouldNormalizeGlobalFiltersNamesAsProperties() {
		List<Class<? extends GlobalFilter>> predicates = Arrays.asList(ForwardPathFilter.class,
				AdaptCachedBodyGlobalFilter.class, WebsocketRoutingFilter.class);

		List<String> resultNames = predicates.stream().map(NameUtils::normalizeGlobalFilterNameAsProperty)
				.collect(Collectors.toList());

		List<String> expectedNames = Arrays.asList("forward-path", "adapt-cached-body", "websocket-routing");

		assertThat(resultNames).isEqualTo(expectedNames);
	}

}
