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

package org.springframework.cloud.gateway.filter.factory;

import org.junit.jupiter.api.Test;

import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.test.BaseWebClientTests;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;

import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

/**
 * @author Fredrich Ombico
 */
@SpringBootTest(webEnvironment = RANDOM_PORT)
@DirtiesContext
class RewriteRequestParameterGatewayFilterFactoryIntegrationTests extends BaseWebClientTests {

	@Test
	void rewriteRequestParameterFilterWorks() {
		testClient.get()
			.uri("/get?campaign=old")
			.header("Host", "www.rewriterequestparameter.org")
			.exchange()
			.expectStatus()
			.isOk()
			.expectBody()
			.jsonPath("$.args.campaign")
			.isEqualTo("fall2023");
	}

	@EnableAutoConfiguration
	@SpringBootConfiguration
	@Import(DefaultTestConfig.class)
	public static class TestConfig {

	}

}
