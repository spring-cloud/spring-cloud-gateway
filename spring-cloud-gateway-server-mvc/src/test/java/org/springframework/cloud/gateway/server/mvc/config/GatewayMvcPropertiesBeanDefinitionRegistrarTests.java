/*
 * Copyright 2013-2023 the original author or authors.
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

package org.springframework.cloud.gateway.server.mvc.config;

import java.util.ArrayList;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.cloud.context.refresh.ContextRefresher;
import org.springframework.cloud.gateway.server.mvc.common.MvcUtils;
import org.springframework.cloud.gateway.server.mvc.test.HttpbinTestcontainers;
import org.springframework.cloud.gateway.server.mvc.test.TestLoadBalancerConfig;
import org.springframework.cloud.gateway.server.mvc.test.client.TestRestClient;
import org.springframework.cloud.loadbalancer.annotation.LoadBalancerClient;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpMethod;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.web.servlet.function.HandlerFunction;
import org.springframework.web.servlet.function.RequestPredicate;
import org.springframework.web.servlet.function.RequestPredicates;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.RouterFunctions;
import org.springframework.web.servlet.function.ServerRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.cloud.gateway.server.mvc.test.TestUtils.getMap;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT,
		properties = { "spring.cloud.gateway.function.enabled=false" })
@ActiveProfiles("propertiesbeandefinitionregistrartests")
@ContextConfiguration(initializers = HttpbinTestcontainers.class)
public class GatewayMvcPropertiesBeanDefinitionRegistrarTests {

	@Autowired
	TestRestClient restClient;

	@Test
	void contextLoads(ApplicationContext context) {
		Map<String, RouterFunction> routerFunctions = getRouterFunctions(context);

		assertThat(routerFunctions).hasSizeGreaterThanOrEqualTo(5)
			.containsKeys("listRoute1", "route1", "route2CustomId", "listRoute2", "listRoute3", "listRoute4");
		RouterFunction listRoute1RouterFunction = routerFunctions.get("listRoute1");
		listRoute1RouterFunction.accept(new AbstractRouterFunctionsVisitor() {
			@Override
			public void route(RequestPredicate predicate, HandlerFunction<?> handlerFunction) {
				predicate.accept(new AbstractRequestPredicatesVisitor() {
					@Override
					public void method(Set<HttpMethod> methods) {
						assertThat(methods).containsOnly(HttpMethod.GET);
					}

					@Override
					public void path(String pattern) {
						assertThat(pattern).isEqualTo("/anything/listRoute1");
					}
				});
			}

			@Override
			public void attributes(Map<String, Object> attributes) {
				assertThat(attributes).containsEntry(MvcUtils.GATEWAY_ROUTE_ID_ATTR, "listRoute1");
			}
		});
		RouterFunction listRoute2RouterFunction = routerFunctions.get("listRoute2");
		final ArrayList<String> paths = new ArrayList<>();

		listRoute2RouterFunction.accept(new AbstractRouterFunctionsVisitor() {
			@Override
			public void route(RequestPredicate predicate, HandlerFunction<?> handlerFunction) {
				predicate.accept(new AbstractRequestPredicatesVisitor() {
					@Override
					public void method(Set<HttpMethod> methods) {
						assertThat(methods).containsOnly(HttpMethod.GET, HttpMethod.POST);
					}

					@Override
					public void path(String pattern) {
						paths.add(pattern);
					}
				});
			}

			@Override
			public void attributes(Map<String, Object> attributes) {
				assertThat(attributes).containsEntry(MvcUtils.GATEWAY_ROUTE_ID_ATTR, "listRoute2");
			}
		});
		assertThat(paths).containsOnly("/anything/listRoute2", "/anything/anotherlistRoute2");

		RouterFunction listRoute3RouterFunction = routerFunctions.get("listRoute3");
		listRoute3RouterFunction.accept(new AbstractRouterFunctionsVisitor() {
			@Override
			public void route(RequestPredicate predicate, HandlerFunction<?> handlerFunction) {
				predicate.accept(new AbstractRequestPredicatesVisitor() {
					@Override
					public void path(String pattern) {
						assertThat(pattern).isEqualTo("/extra/anything/listRoute3");
					}

					@Override
					public void header(String name, String value) {
						assertThat(name).isEqualTo("MyHeaderName");
						assertThat(value).isEqualTo("MyHeader.*");
					}
				});
			}

			@Override
			public void attributes(Map<String, Object> attributes) {
				assertThat(attributes).containsEntry(MvcUtils.GATEWAY_ROUTE_ID_ATTR, "listRoute3");
			}
		});
	}

	private static Map<String, RouterFunction> getRouterFunctions(ApplicationContext context) {
		Map<String, RouterFunction> allRouterFunctions = context.getBeansOfType(RouterFunction.class);
		assertThat(allRouterFunctions).hasSize(1).containsOnlyKeys("gatewayCompositeRouterFunction");
		AtomicReference<Map<String, RouterFunction>> routerFunctionsRef = new AtomicReference<>();
		RouterFunction gatewayCompositeRouterFunction = allRouterFunctions.get("gatewayCompositeRouterFunction");
		gatewayCompositeRouterFunction.accept(new AbstractRouterFunctionsVisitor() {

			@Override
			@SuppressWarnings("unchecked")
			public void attributes(Map<String, Object> attributes) {
				if (attributes.containsKey("gatewayRouterFunctions")) {
					Map<String, RouterFunction> map = (Map<String, RouterFunction>) attributes
						.get("gatewayRouterFunctions");
					routerFunctionsRef.compareAndSet(null, map);
				}
			}

		});
		Map<String, RouterFunction> routerFunctions = routerFunctionsRef.get();
		return routerFunctions;
	}

	@Test
	@SuppressWarnings("unchecked")
	public void configuredRouteWorks() {
		restClient.get()
			.uri("/anything/listRoute1")
			.exchange()
			.expectStatus()
			.isOk()
			.expectBody(Map.class)
			.consumeWith(res -> {
				Map<String, Object> headers = getMap(res.getResponseBody(), "headers");
				assertThat(headers).containsEntry("X-Test", "listRoute1");
			});
	}

	@Test
	@SuppressWarnings("unchecked")
	public void lbRouteWorks() {
		restClient.get()
			.uri("/extra/anything/listRoute3")
			.header("MyHeaderName", "MyHeaderVal")
			.exchange()
			.expectStatus()
			.isOk()
			.expectBody(Map.class)
			.consumeWith(res -> {
				Map<String, Object> headers = getMap(res.getResponseBody(), "headers");
				assertThat(headers).containsEntry("X-Test", "listRoute3");
			});
	}

	@Test
	@SuppressWarnings({ "rawtypes", "unchecked" })
	void refreshWorks(ConfigurableApplicationContext context) {
		Map<String, RouterFunction> routerFunctions = getRouterFunctions(context);
		assertThat(routerFunctions).hasSize(6);
		TestPropertyValues
			.of("spring.cloud.gateway.mvc.routesMap.route3.uri=https://example3.com",
					"spring.cloud.gateway.mvc.routesMap.route3.predicates[0].name=Path",
					"spring.cloud.gateway.mvc.routesMap.route3.predicates[0].args.pattern=/anything/mapRoute3",
					"spring.cloud.gateway.mvc.routesMap.route3.filters[0].Name=HttpbinUriResolver",
					"spring.cloud.gateway.mvc.routesMap.route3.filters[1].Name=AddRequestHeader",
					"spring.cloud.gateway.mvc.routesMap.route3.filters[1].args.name=X-Test",
					"spring.cloud.gateway.mvc.routesMap.route3.filters[1].args.values=mapRoute3")
			.applyTo(context);
		ContextRefresher contextRefresher = context.getBean(ContextRefresher.class);
		contextRefresher.refresh();
		// make http call before getRouterFunction()
		restClient.get()
			.uri("/anything/mapRoute3")
			.exchange()
			.expectStatus()
			.isOk()
			.expectBody(Map.class)
			.consumeWith(res -> {
				Map<String, Object> headers = getMap(res.getResponseBody(), "headers");
				assertThat(headers).containsEntry("X-Test", "mapRoute3");
			});

		GatewayMvcProperties properties = context.getBean(GatewayMvcProperties.class);
		assertThat(properties.getRoutesMap()).hasSize(3).containsKey("route3");
		routerFunctions = getRouterFunctions(context);
		assertThat(routerFunctions).hasSize(7);
	}

	@SpringBootConfiguration
	@EnableAutoConfiguration
	@LoadBalancerClient(name = "httpbin", configuration = TestLoadBalancerConfig.Httpbin.class)
	static class Config {

	}

	static abstract class AbstractRequestPredicatesVisitor implements RequestPredicates.Visitor {

		@Override
		public void method(Set<HttpMethod> methods) {

		}

		@Override
		public void path(String pattern) {

		}

		@Override
		public void pathExtension(String extension) {

		}

		@Override
		public void header(String name, String value) {

		}

		@Override
		public void param(String name, String value) {

		}

		@Override
		public void unknown(RequestPredicate predicate) {

		}

		@Override
		public void startAnd() {

		}

		@Override
		public void and() {

		}

		@Override
		public void endAnd() {

		}

		@Override
		public void startOr() {

		}

		@Override
		public void or() {

		}

		@Override
		public void endOr() {

		}

		@Override
		public void startNegate() {

		}

		@Override
		public void endNegate() {

		}

	}

	static abstract class AbstractRouterFunctionsVisitor implements RouterFunctions.Visitor {

		@Override
		public void startNested(RequestPredicate predicate) {

		}

		@Override
		public void endNested(RequestPredicate predicate) {

		}

		@Override
		public void route(RequestPredicate predicate, HandlerFunction<?> handlerFunction) {

		}

		@Override
		public void resources(Function<ServerRequest, Optional<Resource>> lookupFunction) {

		}

		@Override
		public void attributes(Map<String, Object> attributes) {

		}

		@Override
		public void unknown(RouterFunction<?> routerFunction) {

		}

	}

}
