/*
 * Copyright 2013-2017 the original author or authors.
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

package org.springframework.cloud.gateway.cors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

import java.util.Arrays;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.test.BaseWebClientTests;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.web.reactive.function.client.ClientResponse;

import reactor.core.publisher.Mono;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = RANDOM_PORT)
@DirtiesContext
public class CorsTests extends BaseWebClientTests {

	@Test
	public void testPreFlightCorsRequest() {
		ClientResponse clientResponse = webClient.options().uri("/abc/123/function")
				.header("Origin", "domain.com")
				.header("Access-Control-Request-Method", "GET").exchange().block();
		HttpHeaders asHttpHeaders = clientResponse.headers().asHttpHeaders();
		Mono<String> bodyToMono = clientResponse.bodyToMono(String.class);
		// pre-flight request shouldn't return the response body
		assertNull(bodyToMono.block());
		assertEquals(
				"Missing header value in response: "
						+ HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN,
				"*", asHttpHeaders.getAccessControlAllowOrigin());
		assertEquals(
				"Missing header value in response: "
						+ HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS,
				Arrays.asList(new HttpMethod[] { HttpMethod.GET }),
				asHttpHeaders.getAccessControlAllowMethods());
		assertEquals("Pre Flight call failed.", HttpStatus.OK,
				clientResponse.statusCode());
	}

	@Test
	public void testCorsRequest() {
		ClientResponse clientResponse = webClient.get().uri("/abc/123/function")
				.header("Origin", "domain.com").header(HttpHeaders.HOST, "www.path.org")
				.exchange().block();
		HttpHeaders asHttpHeaders = clientResponse.headers().asHttpHeaders();
		Mono<String> bodyToMono = clientResponse.bodyToMono(String.class);
		assertNotNull(bodyToMono.block());
		assertEquals(
				"Missing header value in response: "
						+ HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN,
				"*", asHttpHeaders.getAccessControlAllowOrigin());
		assertEquals("CORS request failed.", HttpStatus.OK,
				clientResponse.statusCode());
	}

	@EnableAutoConfiguration
	@SpringBootConfiguration
	@Import(DefaultTestConfig.class)
	public static class TestConfig {

	}

}
