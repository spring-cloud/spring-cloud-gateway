/*
 * Copyright 2013-2018 the original author or authors.
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
 *
 */

package org.springframework.cloud.gateway.actuate;

import java.util.List;
import java.util.Map;

import org.junit.Test;
import org.junit.runner.RunWith;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.cloud.gateway.test.PermitAllSecurityConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.reactive.server.WebTestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

@RunWith(SpringRunner.class)
@SpringBootTest(properties = "management.endpoints.web.exposure.include=*", webEnvironment = RANDOM_PORT)
public class GatewayControllerEndpointTests {

	@Autowired
	WebTestClient testClient;

	@LocalServerPort
	int port;

	@Test
	public void testRefresh() {
		testClient.post()
				.uri("http://localhost:"+port+"/actuator/gateway/refresh")
				.exchange()
				.expectStatus().isOk();
	}

	@Test
	public void testRoutes() {
		testClient.get()
				.uri("http://localhost:"+port+"/actuator/gateway/routes")
				.exchange()
				.expectStatus().isOk()
				.expectBodyList(Map.class)
				.consumeWith(result -> {
					List<Map> responseBody = result.getResponseBody();
					assertThat(responseBody).isNotEmpty();
				});
	}

	@SpringBootConfiguration
	@EnableAutoConfiguration
	@Import(PermitAllSecurityConfiguration.class)
	static class TestConfig{}
}
