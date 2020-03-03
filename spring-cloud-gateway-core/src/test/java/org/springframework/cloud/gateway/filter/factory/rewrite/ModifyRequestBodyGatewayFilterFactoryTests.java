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

package org.springframework.cloud.gateway.filter.factory.rewrite;

import org.junit.Test;
import org.junit.runner.RunWith;
import reactor.core.publisher.Mono;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.cloud.gateway.test.BaseWebClientTests;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.web.reactive.function.BodyInserters;

import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

/**
 * @author Junghoon Song
 */
@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = RANDOM_PORT,
		properties = "spring.codec.max-in-memory-size=13")
@DirtiesContext
public class ModifyRequestBodyGatewayFilterFactoryTests extends BaseWebClientTests {

	@Test
	public void modifyRequestBody() {
		testClient.post().uri("/post").header("Host", "www.modifyrequestbody.org")
				.header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_XML_VALUE)
				.body(BodyInserters.fromValue("request")).exchange().expectStatus()
				.isEqualTo(HttpStatus.OK).expectBody().jsonPath("headers.Content-Type")
				.isEqualTo(MediaType.APPLICATION_JSON_VALUE).jsonPath("data")
				.isEqualTo("modifyrequest");
	}

	@Test
	public void upstreamRequestBodyIsEmpty() {
		testClient.post().uri("/post").header("Host", "www.modifyrequestbodyempty.org")
				.header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
				.exchange().expectStatus().isEqualTo(HttpStatus.OK).expectBody()
				.jsonPath("headers.Content-Type")
				.isEqualTo(MediaType.APPLICATION_JSON_VALUE).jsonPath("data")
				.isEqualTo("modifyrequest");
	}

	@Test
	public void modifyRequestBodyToLarge() {
		testClient.post().uri("/post")
				.header("Host", "www.modifyrequestbodyemptytolarge.org")
				.header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_XML_VALUE)
				.body(BodyInserters.fromValue("request")).exchange().expectStatus()
				.isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR).expectBody()
				.jsonPath("message")
				.isEqualTo("Exceeded limit on max bytes to buffer : 13");
	}

	@EnableAutoConfiguration
	@SpringBootConfiguration
	@Import(DefaultTestConfig.class)
	public static class TestConfig {

		@Value("${test.uri}")
		String uri;

		@Bean
		public RouteLocator testRouteLocator(RouteLocatorBuilder builder) {
			return builder.routes().route("test_modify_request_body",
					r -> r.order(-1).host("**.modifyrequestbody.org")
							.filters(f -> f.modifyRequestBody(String.class, String.class,
									MediaType.APPLICATION_JSON_VALUE,
									(serverWebExchange, aVoid) -> {
										return Mono.just("modifyrequest");
									}))
							.uri(uri))
					.route("test_modify_request_body_empty", r -> r.order(-1)
							.host("**.modifyrequestbodyempty.org")
							.filters(f -> f.modifyRequestBody(String.class, String.class,
									MediaType.APPLICATION_JSON_VALUE,
									(serverWebExchange, body) -> {
										if (body == null) {
											return Mono.just("modifyrequest");
										}
										return Mono.just(body.toUpperCase());
									}))
							.uri(uri))
					.route("test_modify_request_body_to_large", r -> r.order(-1)
							.host("**.modifyrequestbodyemptytolarge.org")
							.filters(f -> f.modifyRequestBody(String.class, String.class,
									MediaType.APPLICATION_JSON_VALUE,
									(serverWebExchange, body) -> {
										return Mono.just(
												"tolarge-tolarge-tolarge-tolarge-tolarge-tolarge-tolarge-tolarge-tolarge-tolarge-tolarge-tolarge-tolarge-tolarge-tolarge-tolarge");
									}))
							.uri(uri))
					.build();
		}

	}

}
