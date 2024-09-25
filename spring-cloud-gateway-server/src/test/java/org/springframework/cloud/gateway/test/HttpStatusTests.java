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

import java.util.Map;

import org.junit.jupiter.api.Test;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

@SpringBootTest(webEnvironment = RANDOM_PORT)
@DirtiesContext
@SuppressWarnings("unchecked")
public class HttpStatusTests extends BaseWebClientTests {

	public static void main(String[] args) {
		new SpringApplication(TestConfig.class).run(args);
	}

	@Test
	void notFoundResponseWorks() {
		testClient.get()
			.uri("/status/404")
			.exchange()
			.expectStatus()
			.isEqualTo(HttpStatus.NOT_FOUND)
			.expectBody(String.class)
			.isEqualTo("Failed with 404");
	}

	@Test
	void nonStandardCodeWorks() {
		ResponseEntity<String> response = new TestRestTemplate().getForEntity(baseUri + "/status/432", String.class);
		assertThat(response.getStatusCodeValue()).isEqualTo(432);
		assertThat(response.getBody()).isEqualTo("Failed with 432");

		/*
		 * testClient.get() .uri("/status/432") .exchange() .expectStatus().isEqualTo(432)
		 * .expectBody(String.class).isEqualTo("Failed with 432");
		 */
	}

	@Test
	void serverErrorResponseWorks() {
		testClient.get()
			.uri("/status/500")
			.exchange()
			.expectStatus()
			.isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR)
			.expectBody(String.class)
			.isEqualTo("Failed with 500");
	}

	@Test
	void normalErrorPageWorks() {
		testClient.get()
			.uri("/exception")
			.exchange()
			.expectStatus()
			.isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR)
			.expectBody(Map.class)
			.consumeWith(result -> assertThat(result.getResponseBody()).hasSizeGreaterThanOrEqualTo(5)
				.containsKeys("timestamp", "path", "status", "error", "message"));
	}

	@EnableAutoConfiguration
	@SpringBootConfiguration
	@RestController
	@Import(DefaultTestConfig.class)
	static class TestConfig {

		@GetMapping("/httpbin/exception")
		String exception() {
			throw new RuntimeException("an error");
		}

	}

}
