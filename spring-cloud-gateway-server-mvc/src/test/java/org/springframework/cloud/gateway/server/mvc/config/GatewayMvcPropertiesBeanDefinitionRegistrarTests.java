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

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.cloud.client.DefaultServiceInstance;
import org.springframework.cloud.gateway.server.mvc.ServerMvcIntegrationTests;
import org.springframework.cloud.gateway.server.mvc.test.client.TestRestClient;
import org.springframework.cloud.loadbalancer.annotation.LoadBalancerClient;
import org.springframework.cloud.loadbalancer.core.ServiceInstanceListSupplier;
import org.springframework.cloud.loadbalancer.support.ServiceInstanceListSuppliers;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpMethod;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.servlet.function.HandlerFunction;
import org.springframework.web.servlet.function.RequestPredicate;
import org.springframework.web.servlet.function.RequestPredicates;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.RouterFunctions;
import org.springframework.web.servlet.function.ServerRequest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@ActiveProfiles("propertiesbeandefinitionregistrartests")
public class GatewayMvcPropertiesBeanDefinitionRegistrarTests {

	@Autowired
	TestRestClient restClient;

	@Test
	void contextLoads(ApplicationContext context) {
		Map<String, RouterFunction> routerFunctions = context.getBeansOfType(RouterFunction.class);
		assertThat(routerFunctions).hasSizeGreaterThanOrEqualTo(5).containsKeys("listRoute1RouterFunction",
				"route1RouterFunction", "route2CustomIdRouterFunction", "listRoute2RouterFunction",
				"listRoute3RouterFunction");
		RouterFunction listRoute1RouterFunction = routerFunctions.get("listRoute1RouterFunction");
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
		});
		RouterFunction listRoute2RouterFunction = routerFunctions.get("listRoute2RouterFunction");
		listRoute2RouterFunction.accept(new AbstractRouterFunctionsVisitor() {
			@Override
			public void route(RequestPredicate predicate, HandlerFunction<?> handlerFunction) {
				predicate.accept(new AbstractRequestPredicatesVisitor() {
					@Override
					public void method(Set<HttpMethod> methods) {
						assertThat(methods).containsOnly(HttpMethod.GET, HttpMethod.POST);
					}
				});
			}
		});
		RouterFunction listRoute3RouterFunction = routerFunctions.get("listRoute3RouterFunction");
		listRoute3RouterFunction.accept(new AbstractRouterFunctionsVisitor() {
			@Override
			public void route(RequestPredicate predicate, HandlerFunction<?> handlerFunction) {
				predicate.accept(new AbstractRequestPredicatesVisitor() {
					@Override
					public void path(String pattern) {
						assertThat(pattern).isEqualTo("/anything/listRoute3");
					}

					@Override
					public void header(String name, String value) {
						assertThat(name).isEqualTo("MyHeaderName");
						assertThat(value).isEqualTo("MyHeader.*");
					}
				});
			}
		});
	}

	@Test
	@SuppressWarnings("unchecked")
	public void configuredRouteWorks() {
		restClient.get().uri("/anything/listRoute1").exchange().expectStatus().isOk().expectBody(Map.class)
				.consumeWith(res -> {
					Map<String, Object> map = res.getResponseBody();
					assertThat(map).isNotEmpty().containsKey("headers");
					Map<String, Object> headers = (Map<String, Object>) map.get("headers");
					assertThat(headers).containsEntry("x-test", "listRoute1");
				});
	}

	@Test
	@SuppressWarnings("unchecked")
	public void lbRouteWorks() {
		restClient.get().uri("/anything/listRoute3").header("MyHeaderName", "MyHeaderVal").exchange().expectStatus()
				.isOk().expectBody(Map.class).consumeWith(res -> {
					Map<String, Object> map = res.getResponseBody();
					assertThat(map).isNotEmpty().containsKey("headers");
					Map<String, Object> headers = (Map<String, Object>) map.get("headers");
					assertThat(headers).containsEntry("x-test", "listRoute3");
				});
	}

	@SpringBootConfiguration
	@EnableAutoConfiguration
	@LoadBalancerClient(name = "testservice", configuration = ServerMvcIntegrationTests.TestLoadBalancerConfig.class)
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

	public static class TestLoadBalancerConfig {

		@LocalServerPort
		protected int port = 0;

		@Bean
		public ServiceInstanceListSupplier staticServiceInstanceListSupplier() {
			return ServiceInstanceListSuppliers.from("testservice",
					new DefaultServiceInstance("testservice" + "-1", "testservice", "localhost", port, false));
		}

	}

}
