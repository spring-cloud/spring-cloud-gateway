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

package org.springframework.cloud.gateway.filter.factory;

import org.junit.Test;
import org.junit.runner.RunWith;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.test.BaseWebClientTests;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

/**
 * @author Srinivasa Vasu
 */
@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = RANDOM_PORT)
@DirtiesContext
public class ErrorStatusCodeToExceptionGatewayFilterFactoryTests
		extends BaseWebClientTests {

	@Test
	public void set4xxStatusCodeFilterWorks() {
		testClient.get().uri("/errorresponse/status/code/4xx").exchange().expectStatus()
				.is4xxClientError().expectBody().json("{\"message\": \"Not Found\"}");
	}

	@Test
	public void set5xxStatusCodeFilterWorks() {
		testClient.get().uri("/errorresponse/status/code/5xx").exchange().expectStatus()
				.is5xxServerError().expectBody()
				.json("{\"message\": \"Service Unavailable\"}");
	}

	@Test
	public void toStringFormat() {
		GatewayFilter filter = new ErrorStatusCodeToExceptionGatewayFilterFactory()
				.apply(c -> c.setName("MeshErrorStatusCodeToException"));
		assertThat(filter.toString()).contains("MeshErrorStatusCodeToException");
	}

	@EnableAutoConfiguration
	@SpringBootConfiguration
	@RestController
	@Import(DefaultTestConfig.class)
	public static class TestConfig {

		@Value("${test.uri}")
		String uri;

		@RequestMapping("/httpbin/errorresponse/status/code/4xx")
		public ResponseEntity<?> error4xxResponse(ServerHttpResponse httpResponse) {
			return ResponseEntity.status(HttpStatus.NOT_FOUND).build();

		}

		@RequestMapping("/httpbin/errorresponse/status/code/5xx")
		public ResponseEntity<?> error5xxResponse(ServerHttpResponse httpResponse) {
			return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();

		}

	}

}
