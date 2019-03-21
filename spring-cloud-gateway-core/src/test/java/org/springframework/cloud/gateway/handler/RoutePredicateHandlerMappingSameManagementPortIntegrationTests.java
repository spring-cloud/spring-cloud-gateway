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
 *
 */

package org.springframework.cloud.gateway.handler;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.test.BaseWebClientTests;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.util.SocketUtils;

import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.DEFINED_PORT;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = DEFINED_PORT)
@DirtiesContext
public class RoutePredicateHandlerMappingSameManagementPortIntegrationTests extends BaseWebClientTests {

	private static int samePort;

	@BeforeClass
	public static void beforeClass() {
		samePort = SocketUtils.findAvailableTcpPort();
		System.setProperty("server.port", String.valueOf(samePort));
		System.setProperty("management.server.port", String.valueOf(samePort));
	}

	@AfterClass
	public static void afterClass() {
		System.clearProperty("server.port");
		System.clearProperty("management.server.port");
	}

	@Test
	public void requestsToGatewaySucceed() {
		testClient.mutate().baseUrl("http://localhost:"+ samePort).build()
				.get().uri("/get")
				.exchange()
				.expectStatus().isOk();
	}

	@EnableAutoConfiguration
	@SpringBootConfiguration
	@Import(DefaultTestConfig.class)
	public static class TestConfig { }

}
