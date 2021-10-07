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

package org.springframework.cloud.gateway.cors;

import org.junit.Test;
import org.junit.runner.RunWith;

import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.config.GatewayAutoConfiguration;
import org.springframework.cloud.gateway.test.BaseWebClientTests;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.web.reactive.function.client.ClientResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

/**
 * @author mouxhun
 */
@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = RANDOM_PORT,
		properties = "spring.cloud.gateway.globalcors.pass-through=true")
@DirtiesContext
public class CorsPassThroughTests extends BaseWebClientTests {

	@Test
	public void testCorsPassThrough() {
		ClientResponse clientResponse = webClient.options().uri("/get")
				.header("Origin", "localhost")
				.header("Access-Control-Request-Method", "GET").exchange().block();

		assertThat(clientResponse).isNotNull();
		assertThat(clientResponse.statusCode()).as("CORS PassThrough failed.")
				.isEqualTo(HttpStatus.FORBIDDEN);
	}

	@EnableAutoConfiguration
	@SpringBootConfiguration
	@AutoConfigureBefore(GatewayAutoConfiguration.class)
	@Import(DefaultTestConfig.class)
	public static class TestConfig {

	}

}
