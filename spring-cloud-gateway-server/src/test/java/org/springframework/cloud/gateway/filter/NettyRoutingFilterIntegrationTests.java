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

package org.springframework.cloud.gateway.filter;

import org.junit.Test;
import org.junit.runner.RunWith;
import reactor.core.publisher.Mono;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.test.BaseWebClientTests;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.http.server.reactive.ServerHttpResponseDecorator;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.server.ServerWebExchange;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Offset.offset;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

@RunWith(SpringRunner.class)
@SpringBootTest(properties = "spring.cloud.gateway.httpclient.response-timeout=3s", webEnvironment = RANDOM_PORT)
@DirtiesContext
public class NettyRoutingFilterIntegrationTests extends BaseWebClientTests {

	@Autowired
	private ResponseDecoratingFilter responseDecorator;

	@Test
	public void responseTimeoutWorks() {
		testClient.get().uri("/delay/5").exchange().expectStatus().isEqualTo(HttpStatus.GATEWAY_TIMEOUT).expectBody()
				.jsonPath("$.status").isEqualTo(String.valueOf(HttpStatus.GATEWAY_TIMEOUT.value()))
				.jsonPath("$.message").isEqualTo("Response took longer than timeout: PT3S");
	}

	@Test
	public void outboundHostHeaderNotOverwrittenByInbound() {
		// different base url to have different host header in inbound / outbound requests
		// Host: 127.0.0.1 -> request to Gateway, Host: localhost -> request from Gateway,
		// resolved from lb://testservice
		WebTestClient client = testClient.mutate().baseUrl("http://127.0.0.1:" + port).build();

		client.get().uri("/headers").exchange().expectBody().jsonPath("$.headers.host").isEqualTo("localhost:" + port);
	}

	@Test
	public void canHandleDecoratedResponseWithNonStandardStatusValue() {
		final int NON_STANDARD_STATUS = 480;
		responseDecorator.decorateResponseTimes(1);
		testClient.mutate().baseUrl("http://localhost:" + port).build().get().uri("/status/" + NON_STANDARD_STATUS)
				.exchange().expectStatus().isEqualTo(NON_STANDARD_STATUS);
	}

	@Test
	public void canHandleUndecoratedResponseWithNonStandardStatusValue() {
		final int NON_STANDARD_STATUS = 480;
		responseDecorator.decorateResponseTimes(0);
		testClient.mutate().baseUrl("http://localhost:" + port).build().get().uri("/status/" + NON_STANDARD_STATUS)
				.exchange().expectStatus().isEqualTo(NON_STANDARD_STATUS);
	}

	@Test
	public void canHandleMultiplyDecoratedResponseWithNonStandardStatusValue() {
		final int NON_STANDARD_STATUS = 142;
		responseDecorator.decorateResponseTimes(14);
		testClient.mutate().baseUrl("http://localhost:" + port).build().get().uri("/status/" + NON_STANDARD_STATUS)
				.exchange().expectStatus().isEqualTo(NON_STANDARD_STATUS);
	}

	@Test
	public void shouldApplyConnectTimeoutPerRoute() {
		long currentTimeMillisBeforeCall = System.currentTimeMillis();

		testClient.get().uri("/connect/delay/2").exchange().expectStatus().isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR)
				.expectBody().jsonPath("$.message")
				.value(containsString("Connection refused: localhost/127.0.0.1:32167"));

		// default connect timeout is 45 sec, this test verifies that it is possible to
		// reduce timeout via config
		assertThat(System.currentTimeMillis() - currentTimeMillisBeforeCall).isCloseTo(5, offset(100L));
	}

	@Test
	public void shouldApplyResponseTimeoutPerRoute() {
		testClient.get().uri("/route/delay/2").exchange().expectStatus().isEqualTo(HttpStatus.GATEWAY_TIMEOUT)
				.expectBody().jsonPath("$.status").isEqualTo(String.valueOf(HttpStatus.GATEWAY_TIMEOUT.value()))
				.jsonPath("$.message").isEqualTo("Response took longer than timeout: PT1S");
	}

	@Test
	public void shouldNotApplyPerRouteTimeoutWhenItIsNotConfigured() {
		testClient.get().uri("/delay/2").exchange().expectStatus().isEqualTo(HttpStatus.OK);
	}

	@EnableAutoConfiguration
	@SpringBootConfiguration
	@Import(DefaultTestConfig.class)
	public static class TestConfig {

		@Bean
		@Order(RouteToRequestUrlFilter.HIGHEST_PRECEDENCE)
		public ResponseDecoratingFilter decoratingFilter() {
			return new ResponseDecoratingFilter();
		}

	}

	public static final class ResponseDecoratingFilter implements GlobalFilter, Ordered {

		int decorationIterations = 1;

		public void decorateResponseTimes(int times) {
			decorationIterations = times;
		}

		@Override
		public int getOrder() {
			return RouteToRequestUrlFilter.HIGHEST_PRECEDENCE;
		}

		@Override
		public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
			ServerHttpResponse decorator = exchange.getResponse();
			for (int counter = 0; counter < decorationIterations; counter++) {
				decorator = new ServerHttpResponseDecorator(decorator);
			}
			return chain.filter(exchange.mutate().response(decorator).build());
		}

	}

}
