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

import java.util.Map;

import org.junit.jupiter.api.Test;

import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.test.BaseWebClientTests;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;
import static org.springframework.cloud.gateway.test.TestUtils.getMap;

@SpringBootTest(webEnvironment = RANDOM_PORT)
@DirtiesContext
public class SetRequestHostHeaderGatewayFilterFactoryTests extends BaseWebClientTests {

	@Test
	public void setRequestHostHeaderFilterWorks() {
		testClient.get()
			.uri("/headers")
			.header("Host", "www.setrequesthostheader.org")
			.exchange()
			.expectStatus()
			.isOk()
			.expectBody(Map.class)
			.consumeWith(result -> {
				Map<String, Object> headers = getMap(result.getResponseBody(), "headers");
				assertThat(headers).hasEntrySatisfying("Host", val -> assertThat(val).isEqualTo("otherhost.io"));
			});
	}

	@Test
	public void toStringFormat() {
		SetRequestHostHeaderGatewayFilterFactory.Config config = new SetRequestHostHeaderGatewayFilterFactory.Config();
		config.setHost("myhost");
		GatewayFilter filter = new SetRequestHostHeaderGatewayFilterFactory().apply(config);
		assertThat(filter.toString()).contains("myhost");
	}

	@EnableAutoConfiguration
	@SpringBootConfiguration
	@Import(DefaultTestConfig.class)
	public static class TestConfig {

	}

}
