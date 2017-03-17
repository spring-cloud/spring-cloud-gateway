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

package org.springframework.cloud.gateway.test;

import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.config.GatewayProperties;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.handler.RequestPredicateHandlerMapping;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.server.ServerWebExchange;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;
import static org.springframework.cloud.gateway.test.TestUtils.assertStatus;
import static org.springframework.cloud.gateway.test.TestUtils.getMap;
import static org.springframework.web.reactive.function.BodyExtractors.toMono;

import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = RANDOM_PORT)
@DirtiesContext
@SuppressWarnings("unchecked")
public class GatewayIntegrationTests extends BaseWebClientTests {

	@Autowired
	private GatewayProperties properties;

	@Test
	public void complexContentTypeWorks() {
		Mono<Map> result = webClient.get()
				.uri("/headers")
				.contentType(MediaType.APPLICATION_JSON_UTF8)
				.header("Host", "www.complexcontenttype.org")
				.exchange()
				.then(response -> response.body(toMono(Map.class)));

		StepVerifier.create(result)
				.consumeNextWith(
						response -> {
							Map<String, Object> headers = getMap(response, "headers");
							assertThat(headers).containsEntry(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_UTF8_VALUE);
						})
				.expectComplete()
				.verify(DURATION);
	}

	@Test
	public void compositeRouteWorks() {
		Mono<ClientResponse> result = webClient.get()
				.uri("/headers?foo=bar&baz")
				.header("Host", "www.foo.org")
				.header("X-Request-Id", "123")
				.cookie("chocolate", "chip")
				.exchange();

		StepVerifier.create(result)
				.consumeNextWith(
						response -> {
							assertStatus(response, HttpStatus.OK);
							HttpHeaders httpHeaders = response.headers().asHttpHeaders();
							assertThat(httpHeaders.getFirst(HANDLER_MAPPER_HEADER))
									.isEqualTo(RequestPredicateHandlerMapping.class.getSimpleName());
							assertThat(httpHeaders.getFirst(ROUTE_ID_HEADER))
									.isEqualTo("host_foo_path_headers_to_httpbin");
							assertThat(httpHeaders.getFirst("X-Response-Foo"))
									.isEqualTo("Bar");
						})
				.expectComplete()
				.verify();
	}

	@Test
	public void defaultFiltersWorks() {
		assertThat(this.properties.getDefaultFilters()).isNotEmpty();

		Mono<ClientResponse> result = webClient.get()
				.uri("/headers")
				.header("Host", "www.addresponseheader.org")
				.exchange();

		StepVerifier.create(result)
				.consumeNextWith(
						response -> {
							HttpHeaders httpHeaders = response.headers().asHttpHeaders();
							assertThat(httpHeaders.getFirst("X-Response-Default-Foo"))
									.isEqualTo("Default-Bar");
							assertThat(httpHeaders.get("X-Response-Default-Foo")).hasSize(1);
						})
				.expectComplete()
				.verify(DURATION);
	}

	@Test
	@Ignore
	public void loadBalancerFilterWorks() {
		Mono<ClientResponse> result = webClient.get()
				.uri("/get")
				.header("Host", "www.loadbalancerclient.org")
				.exchange();

		StepVerifier.create(result)
				.consumeNextWith(
						response -> {
							assertStatus(response, HttpStatus.OK);
							HttpHeaders httpHeaders = response.headers().asHttpHeaders();
							assertThat(httpHeaders.getFirst(ROUTE_ID_HEADER))
									.isEqualTo("load_balancer_client_test");
						})
				.expectComplete()
				.verify(DURATION);
	}

	@Test
	@Ignore
	public void postWorks() {
		Mono<Map> result = webClient.post()
				.uri("/post")
				.header("Host", "www.example.org")
				.exchange(Mono.just("testdata"), String.class)
				.then(response -> response.body(toMono(Map.class)));

		StepVerifier.create(result)
				.consumeNextWith(map -> assertThat(map).containsEntry("data", "testdata"))
				.expectComplete()
				.verify(DURATION);
	}

	@EnableAutoConfiguration
	@SpringBootConfiguration
	@Import(DefaultTestConfig.class)
	public static class TestConfig {

		private static final Log log = LogFactory.getLog(TestConfig.class);

		@Bean
		@Order(-1)
		public GlobalFilter postFilter() {
			return (exchange, chain) -> {
				log.info("postFilter start");
				return chain.filter(exchange).then(postFilterWork(exchange));
			};
		}

		private static Mono<Void> postFilterWork(ServerWebExchange exchange) {
			log.info("postFilterWork");
			exchange.getResponse().getHeaders().add("X-Post-Header", "AddedAfterRoute");
			return Mono.empty();
		}

	}

}
