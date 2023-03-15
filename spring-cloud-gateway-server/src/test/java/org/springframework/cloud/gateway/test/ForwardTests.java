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

package org.springframework.cloud.gateway.test;

import java.util.Collections;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

@SpringBootTest(webEnvironment = RANDOM_PORT)
@DirtiesContext
@SuppressWarnings("unchecked")
public class ForwardTests {

	@LocalServerPort
	protected int port = 0;

	protected WebTestClient client;

	@BeforeEach
	public void setup() {
		String baseUri = "http://localhost:" + port;
		this.client = WebTestClient.bindToServer().baseUrl(baseUri).build();
	}

	@Test
	public void forwardWorks() {
		this.client.get().uri("/localcontroller").header(HttpHeaders.HOST, "www.forward.org").exchange().expectStatus()
				.isOk().expectBody().json("{\"from\":\"localcontroller\"}");
	}

	@Test
	public void forwardWithCorrectPath() {
		this.client.get().uri("/foo").header(HttpHeaders.HOST, "www.forward.org").exchange().expectStatus().isOk()
				.expectBody().json("{\"from\":\"localcontroller\"}");
	}

	@EnableAutoConfiguration
	@SpringBootConfiguration
	@RestController
	@Import(PermitAllSecurityConfiguration.class)
	public static class TestConfig {

		@GetMapping("/httpbin/localcontroller")
		public Map<String, String> localController() {
			return Collections.singletonMap("from", "localcontroller");
		}

	}

}
