/*
 * Copyright 2013-present the original author or authors.
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

package org.springframework.cloud.gateway.filter.factory;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractNameValueGatewayFilterFactory.NameValueConfig;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.cloud.gateway.test.BaseWebClientTests;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.test.annotation.DirtiesContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

@SpringBootTest(webEnvironment = RANDOM_PORT)
@DirtiesContext
public class SetResponseHeaderGatewayFilterFactoryTests extends BaseWebClientTests {

	@Test
	public void setResponseHeaderFilterWorks() {
		testClient.get()
			.uri("/headers")
			.header("Host", "www.setreresponseheader.org")
			.exchange()
			.expectStatus()
			.isOk()
			.expectHeader()
			.valueEquals("X-Response-Foo", "Bar");
	}

	@Test
	public void setResponseHeaderFilterWorksJavaDsl() {
		testClient.get()
			.uri("/headers")
			.header("Host", "www.setresponseheaderdsl.org")
			.exchange()
			.expectStatus()
			.isOk()
			.expectHeader()
			.valueEquals("X-Res-Foo", "Second-www");
	}

	@Test
	public void setResponseHeaderSkippedWhenResponseCommitted() {
		MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/").build());
		exchange.getResponse().getHeaders().set("X-Response-Foo", "Original");
		NameValueConfig config = new NameValueConfig().setName("X-Response-Foo").setValue("Bar");
		GatewayFilter filter = new SetResponseHeaderGatewayFilterFactory().apply(config);

		filter.filter(exchange, ex -> ex.getResponse().setComplete()).block();

		assertThat(exchange.getResponse().isCommitted()).isTrue();
		assertThat(exchange.getResponse().getHeaders().get("X-Response-Foo")).containsExactly("Original");
	}

	@Test
	public void toStringFormat() {
		NameValueConfig config = new NameValueConfig().setName("myname").setValue("myvalue");
		GatewayFilter filter = new SetResponseHeaderGatewayFilterFactory().apply(config);
		assertThat(filter.toString()).contains("myname").contains("myvalue");
	}

	@EnableAutoConfiguration
	@SpringBootConfiguration
	@Import(DefaultTestConfig.class)
	public static class TestConfig {

		@Value("${test.uri}")
		String uri;

		@Bean
		public RouteLocator testRouteLocator(RouteLocatorBuilder builder) {
			return builder.routes()
				.route("test_set_response_header_dsl",
						r -> r.order(-1)
							.host("{sub}.setresponseheaderdsl.org")
							.filters(f -> f.prefixPath("/httpbin")
								.setResponseHeader("X-Res-Foo", "Second-{sub}")
								.addResponseHeader("X-Res-Foo", "First"))
							.uri(uri))
				.build();
		}

	}

}
