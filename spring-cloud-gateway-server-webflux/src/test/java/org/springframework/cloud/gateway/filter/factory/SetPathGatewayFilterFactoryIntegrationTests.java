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

package org.springframework.cloud.gateway.filter.factory;

import org.junit.jupiter.api.Test;

import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.test.BaseWebClientTests;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;

import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

@SpringBootTest(webEnvironment = RANDOM_PORT)
@DirtiesContext
public class SetPathGatewayFilterFactoryIntegrationTests extends BaseWebClientTests {

	@Test
	public void setPathFilterDefaultValuesWork() {
		testClient.get()
			.uri("/foo/get")
			.header("Host", "www.setpath.org")
			.exchange()
			.expectStatus()
			.isOk()
			.expectHeader()
			.valueEquals(ROUTE_ID_HEADER, "set_path_test");
	}

	@Test
	public void setPathViaHostFilterWork() {
		testClient.get()
			.uri("/")
			.header("Host", "get.setpathhost.org")
			.exchange()
			.expectStatus()
			.isOk()
			.expectHeader()
			.valueEquals(ROUTE_ID_HEADER, "set_path_host_test");
	}

	@EnableAutoConfiguration
	@SpringBootConfiguration
	@Import(DefaultTestConfig.class)
	public static class TestConfig {

	}

}
