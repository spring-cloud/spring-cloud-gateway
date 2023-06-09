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
import java.util.function.Function;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.cloud.gateway.server.mvc.test.TestRestClient;
import org.springframework.context.ApplicationContext;
import org.springframework.core.io.Resource;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.servlet.function.HandlerFunction;
import org.springframework.web.servlet.function.RequestPredicate;
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
		assertThat(routerFunctions).hasSizeGreaterThanOrEqualTo(3).containsKeys("listRoute1RouterFunction",
				"route1RouterFunction", "route2CustomIdRouterFunction");
		RouterFunction listRoute1RouterFunction = routerFunctions.get("listRoute1RouterFunction");
		listRoute1RouterFunction.accept(new RouterFunctions.Visitor() {
			@Override
			public void startNested(RequestPredicate predicate) {
			}

			@Override
			public void endNested(RequestPredicate predicate) {
			}

			@Override
			public void route(RequestPredicate predicate, HandlerFunction<?> handlerFunction) {
				assertThat(predicate.toString()).isEqualTo("GET");
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
		});
	}

	@Test
	public void configuredRouteWorks() {
		restClient.get().uri("/anything/listRoute1").exchange().expectStatus().isOk().expectBody(Map.class)
				.consumeWith(res -> {
					Map<String, Object> map = res.getResponseBody();
					assertThat(map).isNotEmpty().containsKey("headers");
					Map<String, Object> headers = (Map<String, Object>) map.get("headers");
					assertThat(headers).containsEntry("x-test", "listRoute1");
				});
	}

	@SpringBootConfiguration
	@EnableAutoConfiguration
	static class Config {

	}

}
