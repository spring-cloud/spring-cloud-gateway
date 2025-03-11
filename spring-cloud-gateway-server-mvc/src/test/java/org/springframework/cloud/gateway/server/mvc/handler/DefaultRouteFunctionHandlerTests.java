/*
 * Copyright 2013-2025 the original author or authors.
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

package org.springframework.cloud.gateway.server.mvc.handler;

import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.server.mvc.test.client.TestRestClient;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {}, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class DefaultRouteFunctionHandlerTests {

	@Autowired
	private TestRestClient restClient;

	@Test
	public void testSupplierWorks() {
		restClient.get()
			.uri("/hello")
			.accept(MediaType.TEXT_PLAIN)
			.exchange()
			.expectStatus()
			.isOk()
			.expectBody(String.class)
			.isEqualTo("hello");
	}

	@Test
	public void testFunctionWorksGET() {
		restClient.get()
			.uri("/upper/bob")
			.accept(MediaType.TEXT_PLAIN)
			.exchange()
			.expectStatus()
			.isOk()
			.expectBody(String.class)
			.isEqualTo("BOB");
	}

	@Test
	public void testFunctionWorksPOST() {
		restClient.post()
			.uri("/upper")
			.accept(MediaType.APPLICATION_JSON)
			.bodyValue("bob")
			.exchange()
			.expectStatus()
			.isOk()
			.expectBody(String.class)
			.isEqualTo("BOB");
	}

	@Test
	public void testConsumerWorksGET() {
		restClient.get().uri("/consume/hello").accept(MediaType.TEXT_PLAIN).exchange().expectStatus().isAccepted();
		assertThat(TestConfiguration.consumerInvoked).isTrue();
	}

	@Test
	public void testConsumerWorksPOST() {
		restClient.post()
			.uri("/consume")
			.accept(MediaType.APPLICATION_JSON)
			.bodyValue("hello")
			.exchange()
			.expectStatus()
			.isAccepted();
		assertThat(TestConfiguration.consumerInvoked).isTrue();
	}

	@SpringBootConfiguration
	@EnableAutoConfiguration
	protected static class TestConfiguration {

		static boolean consumerInvoked;

		@Bean
		Function<String, String> upper() {
			return s -> s.toUpperCase(Locale.ROOT);
		}

		@Bean
		Consumer<String> consume() {
			return s -> {
				consumerInvoked = false;
				assertThat(s).isEqualTo("hello");
				consumerInvoked = true;
			};
		}

		@Bean
		Supplier<String> hello() {
			return () -> "hello";
		}

	}

}
