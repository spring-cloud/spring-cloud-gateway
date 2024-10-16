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

import org.junit.jupiter.api.Test;

import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.test.BaseWebClientTests;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.server.ServerWebExchange;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

/**
 * This test just avoid class cast exception with YAML or Properties parsing.
 *
 * {@link NettyRoutingFilter#getHttpClient(Route, ServerWebExchange)}
 * {@link NettyRoutingFilter#getResponseTimeout(Route)}
 *
 * @author echooymxq
 **/
@SpringBootTest(webEnvironment = RANDOM_PORT)
@DirtiesContext
@ActiveProfiles("netty-routing-filter")
class NettyRoutingFilterCompatibleTests extends BaseWebClientTests {

	@Test
	void shouldApplyConnectTimeoutPerRoute() {
		assertThat(NettyRoutingFilter.getInteger("5")).isEqualTo(5);
		assertThat(NettyRoutingFilter.getInteger(5)).isEqualTo(5);
	}

	@Test
	void getLongHandlesStringAndNumber() {
		assertThat(NettyRoutingFilter.getLong("5")).isEqualTo(5);
		assertThat(NettyRoutingFilter.getLong(5)).isEqualTo(5);
		assertThat(NettyRoutingFilter.getLong(50000L)).isEqualTo(50000);
		assertThat(NettyRoutingFilter.getLong(null)).isNull();
		assertThatThrownBy(() -> NettyRoutingFilter.getLong("notanumber")).isInstanceOf(NumberFormatException.class);
	}

	@Test
	void shouldApplyResponseTimeoutPerRoute() {
		testClient.get()
			.uri("/route/delay/2")
			.exchange()
			.expectStatus()
			.isEqualTo(HttpStatus.GATEWAY_TIMEOUT)
			.expectBody()
			.jsonPath("$.status")
			.isEqualTo(String.valueOf(HttpStatus.GATEWAY_TIMEOUT.value()))
			.jsonPath("$.message")
			.isEqualTo("Response took longer than timeout: PT1S");
	}

	@EnableAutoConfiguration
	@SpringBootConfiguration
	@Import(DefaultTestConfig.class)
	static class TestConfig {

	}

}
