/*
 * Copyright 2017-2019 the original author or authors.
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
import java.util.Arrays;
import java.util.List;

import org.junit.Test;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import org.springframework.cloud.gateway.config.GatewayProperties;
import org.springframework.cloud.gateway.config.PropertiesRouteDefinitionLocator;
import org.springframework.cloud.gateway.filter.FilterDefinition;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.OrderedGatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.cloud.gateway.filter.factory.AddResponseHeaderGatewayFilterFactory;
import org.springframework.cloud.gateway.filter.factory.GatewayFilterFactory;
import org.springframework.cloud.gateway.filter.factory.RemoveResponseHeaderGatewayFilterFactory;
import org.springframework.cloud.gateway.handler.predicate.HostRoutePredicateFactory;
import org.springframework.cloud.gateway.handler.predicate.PredicateDefinition;
import org.springframework.cloud.gateway.handler.predicate.RoutePredicateFactory;
import org.springframework.cloud.gateway.support.ConfigurationService;
import org.springframework.util.StringUtils;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Toshiaki Maki
 */
public class RouteDefinitionRouteLocatorTests {

	@Test
	public void contextLoads() {
		List<RoutePredicateFactory> predicates = Arrays
				.asList(new HostRoutePredicateFactory());
		List<GatewayFilterFactory> gatewayFilterFactories = Arrays.asList(
				new RemoveResponseHeaderGatewayFilterFactory(),
				new AddResponseHeaderGatewayFilterFactory(),
				new TestOrderedGatewayFilterFactory());
		GatewayProperties gatewayProperties = new GatewayProperties();
		gatewayProperties.setRoutes(Arrays.asList(new RouteDefinition() {
			{
				setId("foo");
				setUri(URI.create("https://foo.example.com"));
				setPredicates(
						Arrays.asList(new PredicateDefinition("Host=*.example.com")));
				setFilters(Arrays.asList(
						new FilterDefinition("RemoveResponseHeader=Server"),
						new FilterDefinition("TestOrdered="),
						new FilterDefinition("AddResponseHeader=X-Response-Foo, Bar")));
			}
		}));

		PropertiesRouteDefinitionLocator routeDefinitionLocator = new PropertiesRouteDefinitionLocator(
				gatewayProperties);
		@SuppressWarnings("deprecation")
		RouteDefinitionRouteLocator routeDefinitionRouteLocator = new RouteDefinitionRouteLocator(
				new CompositeRouteDefinitionLocator(Flux.just(routeDefinitionLocator)),
				predicates, gatewayFilterFactories, gatewayProperties,
				new ConfigurationService(null, () -> null, () -> null));

		StepVerifier.create(routeDefinitionRouteLocator.getRoutes()).assertNext(route -> {
			List<GatewayFilter> filters = route.getFilters();
			assertThat(filters).hasSize(3);
			assertThat(getFilterClassName(filters.get(0)))
					.contains("RemoveResponseHeader");
			assertThat(getFilterClassName(filters.get(1))).contains("AddResponseHeader");
			assertThat(getFilterClassName(filters.get(2)))
					.contains("RouteDefinitionRouteLocatorTests$TestOrderedGateway");
		}).expectComplete().verify();
	}

	@Test
	public void contextLoadsWithErrorRecovery() {
		List<RoutePredicateFactory> predicates = Arrays
				.asList(new HostRoutePredicateFactory());
		List<GatewayFilterFactory> gatewayFilterFactories = Arrays.asList(
				new RemoveResponseHeaderGatewayFilterFactory(),
				new AddResponseHeaderGatewayFilterFactory(),
				new TestOrderedGatewayFilterFactory());
		GatewayProperties gatewayProperties = new GatewayProperties();
		gatewayProperties.setRoutes(containsInvalidRoutes());
		gatewayProperties.setFailOnRouteDefinitionError(false);

		PropertiesRouteDefinitionLocator routeDefinitionLocator = new PropertiesRouteDefinitionLocator(
				gatewayProperties);
		@SuppressWarnings("deprecation")
		RouteDefinitionRouteLocator routeDefinitionRouteLocator = new RouteDefinitionRouteLocator(
				new CompositeRouteDefinitionLocator(Flux.just(routeDefinitionLocator)),
				predicates, gatewayFilterFactories, gatewayProperties,
				new ConfigurationService(null, () -> null, () -> null));

		StepVerifier.create(routeDefinitionRouteLocator.getRoutes()).assertNext(route -> {
			List<GatewayFilter> filters = route.getFilters();
			assertThat(filters).hasSize(3);
			assertThat(getFilterClassName(filters.get(0)))
					.contains("RemoveResponseHeader");
			assertThat(getFilterClassName(filters.get(1))).contains("AddResponseHeader");
			assertThat(getFilterClassName(filters.get(2)))
					.contains("RouteDefinitionRouteLocatorTests$TestOrderedGateway");
		}).expectComplete().verify();
	}

	private List<RouteDefinition> containsInvalidRoutes() {
		RouteDefinition foo = new RouteDefinition();
		foo.setId("foo");
		foo.setUri(URI.create("https://foo.example.com"));
		foo.setPredicates(Arrays.asList(new PredicateDefinition("Host=*.example.com")));
		foo.setFilters(Arrays.asList(new FilterDefinition("RemoveResponseHeader=Server"),
				new FilterDefinition("TestOrdered="),
				new FilterDefinition("AddResponseHeader=X-Response-Foo, Bar")));
		RouteDefinition bad = new RouteDefinition();
		bad.setId("exceptionRaised");
		bad.setUri(URI.create("https://foo.example.com"));
		bad.setPredicates(Arrays.asList(new PredicateDefinition("Host=*.example.com")));
		bad.setFilters(Arrays.asList(new FilterDefinition("Generate exception")));
		return Arrays.asList(foo, bad);
	}

	private String getFilterClassName(GatewayFilter target) {
		if (target instanceof OrderedGatewayFilter) {
			return getFilterClassName(((OrderedGatewayFilter) target).getDelegate());
		}
		else {
			String simpleName = target.getClass().getSimpleName();
			if (StringUtils.isEmpty(simpleName)) {
				// maybe a lambda using new toString methods
				simpleName = target.toString();
			}
			return simpleName;
		}
	}

	static class TestOrderedGatewayFilterFactory extends AbstractGatewayFilterFactory {

		@Override
		public GatewayFilter apply(Object config) {
			return new OrderedGatewayFilter((exchange, chain) -> chain.filter(exchange),
					9999);
		}

	}

}
