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

import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.test.BaseWebClientTests;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.web.server.ServerWebExchange;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

/**
 * This test just avoid class cast exception with YAML or Properties parsing.
 *
 * {@link NettyRoutingFilter#getHttpClient(Route, ServerWebExchange)}
 * {@link NettyRoutingFilter#getResponseTimeout(Route)}
 *
 * @author echooymxq
 **/
@RunWith(SpringRunner.class)
@SpringBootTest(properties = { "spring.cloud.gateway.routes[0].id=route_connect_timeout",
		"spring.cloud.gateway.routes[0].uri=http://localhost:32167",
		"spring.cloud.gateway.routes[0].predicates[0].name=Path",
		"spring.cloud.gateway.routes[0].predicates[0].args[pattern]=/connect/delay/{timeout}",
		"spring.cloud.gateway.routes[0].metadata[connect-timeout]=5",
		"spring.cloud.gateway.routes[1].id=route_response_timeout",
		"spring.cloud.gateway.routes[1].uri=lb://testservice", "spring.cloud.gateway.routes[1].predicates[0].name=Path",
		"spring.cloud.gateway.routes[1].predicates[0].args[pattern]=/route/delay/{timeout}",
		"spring.cloud.gateway.routes[1].filters[0]=StripPrefix=1",
		"spring.cloud.gateway.routes[1].metadata.response-timeout=1000" }, webEnvironment = RANDOM_PORT)
@DirtiesContext
public class NettyRoutingFilterCompatibleTests extends BaseWebClientTests {

	@Test
	public void shouldApplyConnectTimeoutPerRoute() {
		assertThat(NettyRoutingFilter.getInteger("5")).isEqualTo(5);
		assertThat(NettyRoutingFilter.getInteger(5)).isEqualTo(5);
	}

	@Test
	public void shouldApplyResponseTimeoutPerRoute() {
		testClient.get().uri("/route/delay/2").exchange().expectStatus().isEqualTo(HttpStatus.GATEWAY_TIMEOUT)
				.expectBody().jsonPath("$.status").isEqualTo(String.valueOf(HttpStatus.GATEWAY_TIMEOUT.value()))
				.jsonPath("$.message").isEqualTo("Response took longer than timeout: PT1S");
	}

	@EnableAutoConfiguration
	@SpringBootConfiguration
	@Import(DefaultTestConfig.class)
	public static class TestConfig {

	}

}
