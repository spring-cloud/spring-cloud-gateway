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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Offset.offset;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

/**
 * This test just avoid class cast exception with YAML or Properties parsing.
 *
 * {@link NettyRoutingFilter#httpClientWithTimeoutFrom(Route)}
 * {@link NettyRoutingFilter#getResponseTimeout(Route)}
 *
 * @see <a href="https://github.com/spring-cloud/spring-cloud-gateway/pull/1522">Compatible Configuration</a>
 *
 * @author echooymxq
 **/
@RunWith(SpringRunner.class)
@SpringBootTest(properties = {
		"spring.cloud.gateway.routes[0].id=route_connect_timeout",
		"spring.cloud.gateway.routes[0].uri=http://localhost:32167",
		"spring.cloud.gateway.routes[0].predicates[0].name=Path",
		"spring.cloud.gateway.routes[0].predicates[0].args[pattern]=/connect/delay/{timeout}",
		"spring.cloud.gateway.routes[0].metadata[connect-timeout]=5",
		"spring.cloud.gateway.routes[1].id=route_response_timeout",
		"spring.cloud.gateway.routes[1].uri=lb://testservice",
		"spring.cloud.gateway.routes[1].predicates[0].name=Path",
		"spring.cloud.gateway.routes[1].predicates[0].args[pattern]=/route/delay/{timeout}",
		"spring.cloud.gateway.routes[1].filters[0]=StripPrefix=1",
		"spring.cloud.gateway.routes[1].metadata.response-timeout=1000"},
		webEnvironment = RANDOM_PORT)
@DirtiesContext
public class NettyRoutingFilterCompatibleTests extends BaseWebClientTests {

	@Test
	public void shouldApplyConnectTimeoutPerRoute() {
		long currentTimeMillisBeforeCall = System.currentTimeMillis();

		testClient.get().uri("/connect/delay/2").exchange().expectStatus()
				.isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR).expectBody()
				.jsonPath("$.message")
				.value(containsString("Connection refused: localhost/127.0.0.1:32167"));

		// default connect timeout is 45 sec, this test verifies that it is possible to
		// reduce timeout via config
		assertThat(System.currentTimeMillis() - currentTimeMillisBeforeCall).isCloseTo(5,
				offset(100L));
	}

	@Test
	public void shouldApplyResponseTimeoutPerRoute() {
		testClient.get().uri("/route/delay/2").exchange().expectStatus()
				.isEqualTo(HttpStatus.GATEWAY_TIMEOUT).expectBody().jsonPath("$.status")
				.isEqualTo(String.valueOf(HttpStatus.GATEWAY_TIMEOUT.value()))
				.jsonPath("$.message")
				.isEqualTo("Response took longer than timeout: PT1S");
	}

	@EnableAutoConfiguration
	@SpringBootConfiguration
	@Import(DefaultTestConfig.class)
	public static class TestConfig {

	}

}
