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

package org.springframework.cloud.gateway.handler;

import org.junit.jupiter.api.Test;

import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.test.BaseWebClientTests;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

@SpringBootTest(webEnvironment = RANDOM_PORT)
@DirtiesContext
@ActiveProfiles("pre-flight-request")
public class RoutePredicateHandlerMappingPreFlightRequestTests extends BaseWebClientTests {

	@Test
	public void allowedByUpStreamTest() {
		testClient.mutate().build().options().uri("/allowedbyupstream")
				.header("Origin", "http://www.passthroughpreflight.org").header("Access-Control-Request-Method", "PUT")
				.exchange().expectStatus().is2xxSuccessful().expectHeader()
				.valueEquals("Access-Control-Allow-Methods", "PUT");
	}

	@Test
	public void rejectedByUpStreamTest() {
		testClient.mutate().build().options().uri("/rejectedbyupstream")
				.header("Origin", "http://www.passthroughpreflight.org").header("Access-Control-Request-Method", "GET")
				.exchange().expectStatus().isForbidden();
	}

	@Test
	public void rejectedByNotFoundUpstreamTest() {
		testClient.mutate().build().options().uri("/rejectedbynotfoundupstream")
				.header("Origin", "http://www.passthroughpreflight.org").header("Access-Control-Request-Method", "POST")
				.exchange().expectStatus().isForbidden();
	}

	@Test
	public void allowedByGatewayTest() {
		testClient.mutate().build().options().uri("/allowedbygateway")
				.header("Origin", "http://www.passthroughpreflight.org").header("Access-Control-Request-Method", "POST")
				.exchange().expectStatus().is2xxSuccessful().expectHeader()
				.valueEquals("Access-Control-Allow-Methods", "GET,POST");
	}

	@Test
	public void rejectedByGatewayTest() {
		testClient.mutate().build().options().uri("/rejectedbygateway")
				.header("Origin", "http://www.passthroughpreflight.org").header("Access-Control-Request-Method", "PUT")
				.exchange().expectStatus().isForbidden();
	}

	@EnableAutoConfiguration
	@SpringBootConfiguration
	@Import(DefaultTestConfig.class)
	@RestController
	public static class TestConfig {

		@RequestMapping("/httpbin/allowedbyupstream")
		@CrossOrigin(methods = RequestMethod.PUT)
		String allowedByUpstream() {
			return "preflight request";
		}

		@RequestMapping("/httpbin/rejectedbyupstream")
		@CrossOrigin(methods = RequestMethod.POST)
		String rejectedByUpstream() {
			return "preflight request";
		}

		@RequestMapping("/httpbin/rejectedbygateway")
		@CrossOrigin(methods = RequestMethod.PUT)
		String rejectedByGateway() {
			return "preflight request";
		}
	}

}
