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

package org.springframework.cloud.gateway.handler;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.test.BaseWebClientTests;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.util.MultiValueMap;
import org.springframework.util.SocketUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ServerWebExchange;

import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = RANDOM_PORT, properties = "management.server.port=${test.port}")
@DirtiesContext
public class CustomizeRouteResolveHandlerMappingTest extends BaseWebClientTests {

	private static int managementPort;

	@BeforeClass
	public static void beforeClass() {
		managementPort = SocketUtils.findAvailableTcpPort();
		System.setProperty("test.port", String.valueOf(managementPort));
	}

	@AfterClass
	public static void afterClass() {
		System.clearProperty("test.port");
	}

	@Test
	public void requestsToCustomizeRoute() {
		testClient.get().uri("/openapi?apiName=openapi.trade-order.create&apiVersion=1.0.0&orderId=123456")
				.exchange().expectBody(String.class).isEqualTo("123456");

		testClient.get().uri("/openapi?apiName=openapi.trade-order.delete&apiVersion=1.0.0&orderId=6666666")
				.exchange().expectBody(String.class).isEqualTo("6666666");
	}

	@EnableAutoConfiguration
	@SpringBootConfiguration
	@Import(DefaultTestConfig.class)
	public static class TestConfig {

		@GetMapping("/trade-order/create")
		String create(@RequestParam(name = "orderId", defaultValue = "123456") String orderId) {
			return orderId;
		}

		@GetMapping("/trade-order/delete")
		String delete(@RequestParam(name = "orderId", defaultValue = "123456") String orderId) {
			return orderId;
		}

		@Bean
		AbstractCustomizeRouteResolveHandlerMapping myCustomizeRouteResolveHandlerMapping() {
			return new AbstractCustomizeRouteResolveHandlerMapping() {
				@Override
				protected String resolveRouteId(ServerWebExchange serverWebExchange) {
					MultiValueMap<String, String> queryParams = serverWebExchange.getRequest().getQueryParams();
					String apiName = queryParams.getFirst("apiName");
					String apiVersion = queryParams.getFirst("apiVersion");

					String routeId = apiName + "#" + apiVersion;

					return routeId;
				}
			};
		}

		@Bean
		RouteLocator testRouteLocator(RouteLocatorBuilder builder) {
			return builder.routes()
					.route("openapi.trade-order.create#1.0.0",
							r -> r.query("apiName","openapi.trade-order.create").and()
									.query("apiVersion","1.0.0").uri("/trade-order/create"))
					.route("openapi.trade-order.delete#1.0.0",
							r -> r.query("apiName","openapi.trade-order.delete").and()
									.query("apiVersion","1.0.0").uri("/trade-order/delete"))
					.build();
		}
	}
}
