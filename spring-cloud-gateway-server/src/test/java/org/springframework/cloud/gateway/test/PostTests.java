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
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

@SpringBootTest(webEnvironment = RANDOM_PORT)
@DirtiesContext
@SuppressWarnings("unchecked")
// TODO: why does this have to be in a separate test?
class PostTests extends BaseWebClientTests {

	@Test
	void postWorks() {
		Mono<Map> result = webClient.post()
			.uri("/post")
			.header("Host", "www.example.org")
			.bodyValue("testdata")
			.retrieve()
			.bodyToMono(Map.class);

		StepVerifier.create(result)
			.consumeNextWith(map -> assertThat(map).containsEntry("data", "testdata"))
			.expectComplete()
			.verify(DURATION);
	}

	@EnableAutoConfiguration
	@SpringBootConfiguration
	@Import(DefaultTestConfig.class)
	static class TestConfig {

	}

}
