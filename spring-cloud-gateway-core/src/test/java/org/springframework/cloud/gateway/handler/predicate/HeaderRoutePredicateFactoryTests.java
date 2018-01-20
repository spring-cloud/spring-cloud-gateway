/*
 * Copyright 2013-2018 the original author or authors.
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

package org.springframework.cloud.gateway.handler.predicate;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.handler.RoutePredicateHandlerMapping;
import org.springframework.cloud.gateway.test.BaseWebClientTests;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.web.reactive.function.client.ClientResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;
import static org.springframework.cloud.gateway.test.TestUtils.assertStatus;

import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = RANDOM_PORT)
@DirtiesContext
public class HeaderRoutePredicateFactoryTests extends BaseWebClientTests {

	@Test
	public void headerRouteWorks() {
		Mono<ClientResponse> result = webClient.get()
				.uri("/get")
				.header("Foo", "bar")
				.exchange();

		StepVerifier.create(result)
				.consumeNextWith(response -> {
					assertStatus(response, HttpStatus.OK);
                    HttpHeaders httpHeaders = response.headers().asHttpHeaders();
                    assertThat(httpHeaders.getFirst(HANDLER_MAPPER_HEADER))
                            .isEqualTo(RoutePredicateHandlerMapping.class.getSimpleName());
                    assertThat(httpHeaders.getFirst(ROUTE_ID_HEADER)).isEqualTo("header_test");
                })
				.expectComplete()
				.verify(DURATION);
	}

	@Test
	@SuppressWarnings("Duplicates")
	public void headerRouteIgnoredWhenHeaderMissing() {
		Mono<ClientResponse> result = webClient.get()
				.uri("/get")
				// no headers set. Test used to throw a null pointer exception.
				.exchange();

		StepVerifier.create(result)
				.consumeNextWith(response -> {
                    assertStatus(response, HttpStatus.OK);
                    HttpHeaders httpHeaders = response.headers().asHttpHeaders();
                    assertThat(httpHeaders.getFirst(HANDLER_MAPPER_HEADER))
                            .isEqualTo(RoutePredicateHandlerMapping.class.getSimpleName());
                    assertThat(httpHeaders.getFirst(ROUTE_ID_HEADER))
                            .isEqualTo("default_path_to_httpbin");
                })
				.expectComplete()
				.verify(DURATION);
	}

	@EnableAutoConfiguration
	@SpringBootConfiguration
	@Import(DefaultTestConfig.class)
	public static class TestConfig { }

}
