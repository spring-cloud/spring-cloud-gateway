/*
 * Copyright 2013-2021 the original author or authors.
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

import java.util.Arrays;

import org.junit.Test;
import org.junit.runner.RunWith;
import reactor.core.publisher.Mono;

import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.config.GatewayAutoConfiguration;
import org.springframework.cloud.gateway.test.BaseWebClientTests;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
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
		properties = { "spring.cloud.gateway.globalcors.pass-through=true" })
@DirtiesContext
public class CorsPassThroughTests extends BaseWebClientTests {

	@Test
	public void testCorsPreflightRequestPassThrough() {
		ClientResponse clientResponse = webClient.options().uri("/cors")
				.header("Host", "cors.example.org").header("Origin", "remoteHost")
				.header("Access-Control-Request-Method", "GET").exchange().block();

		HttpHeaders asHttpHeaders = clientResponse.headers().asHttpHeaders();
		Mono<String> bodyToMono = clientResponse.bodyToMono(String.class);
		// pre-flight request shouldn't return the response body
		assertThat(bodyToMono.block()).isNull();
		assertThat(asHttpHeaders.getAccessControlAllowOrigin())
				.as("Missing header value in response: "
						+ HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN)
				.isEqualTo("*");
		assertThat(asHttpHeaders.getAccessControlAllowMethods())
				.as("Missing header value in response: "
						+ HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS)
				.isEqualTo(Arrays
						.asList(new HttpMethod[] { HttpMethod.GET, HttpMethod.POST }));
		assertThat(clientResponse.statusCode())
				.as("CORS Preflight request PassThrough failed.")
				.isEqualTo(HttpStatus.OK);
	}

	@Test
	public void testCorsNonPreflightRequestPassThrough() {
		ClientResponse clientResponse = webClient.get().uri("/cors")
				.header("Origin", "remoteHost")
				.header(HttpHeaders.HOST, "cors.example.org").exchange().block();

		HttpHeaders asHttpHeaders = clientResponse.headers().asHttpHeaders();
		Mono<String> bodyToMono = clientResponse.bodyToMono(String.class);

		assertThat(bodyToMono.block()).isNotNull();
		assertThat(asHttpHeaders.getAccessControlAllowOrigin())
				.as("Missing header value in response: "
						+ HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN)
				.isEqualTo("*");
		assertThat(clientResponse.statusCode())
				.as("CORS NonPreflight request PassThrough failed.")
				.isEqualTo(HttpStatus.OK);
	}

	@EnableAutoConfiguration
	@SpringBootConfiguration
	@AutoConfigureBefore(GatewayAutoConfiguration.class)
	@Import(DefaultTestConfig.class)
	public static class TestConfig {

	}

}
