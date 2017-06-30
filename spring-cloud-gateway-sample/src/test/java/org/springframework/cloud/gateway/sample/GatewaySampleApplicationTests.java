/*
 * Copyright 2013-2017 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package org.springframework.cloud.gateway.sample;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

/**
 * @author Spencer Gibb
 */
@RunWith(SpringRunner.class)
@SpringBootTest(classes = GatewaySampleApplication.class, webEnvironment = RANDOM_PORT)
public class GatewaySampleApplicationTests {

	@LocalServerPort
	protected int port = 0;

	protected WebClient webClient;
	protected String baseUri;

	@Before
	public void setup() {
		baseUri = "http://localhost:" + port;
		this.webClient = WebClient.create(baseUri);
	}

	@Test
	public void contextLoads() {
		Mono<ClientResponse> result = webClient.get()
				.uri("/get")
				.exchange();

		StepVerifier.create(result)
				.consumeNextWith(
						response -> {
							assertThat(response.statusCode()).isEqualTo(HttpStatus.OK);
							HttpHeaders httpHeaders = response.headers().asHttpHeaders();
						})
				.expectComplete()
				.verify(Duration.ofSeconds(5));
	}
}
