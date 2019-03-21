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

package org.springframework.cloud.gateway.filter.headers;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.cloud.gateway.test.BaseWebClientTests;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.DEFINED_PORT;

@RunWith(SpringRunner.class)
@SpringBootTest(
		webEnvironment = DEFINED_PORT,
		properties = {"server.port=62175"}
)
@DirtiesContext
public class NonStandardHeadersInResponseTests extends BaseWebClientTests {

	public static final String CONTENT_TYPE_IMAGE = "image";

	@Test
	public void nonStandardHeadersInResponse() {
		URI uri = UriComponentsBuilder
				.fromUriString(this.baseUri + "/get-image")
				.build(true)
				.toUri();

		String contentType = WebClient.builder()
				.baseUrl(baseUri)
				.build()
				.get()
				.uri(uri)
				.exchange()
				.map(clientResponse -> clientResponse.headers().asHttpHeaders().getFirst(HttpHeaders.CONTENT_TYPE))
				.block();

		assertEquals(CONTENT_TYPE_IMAGE, contentType);
	}

	@EnableAutoConfiguration
	@SpringBootConfiguration
	@Import(DefaultTestConfig.class)
	public static class TestConfig {
		private static final Log log = LogFactory.getLog(TestConfig.class);
		@Value("${test.uri}")
		String uri;
		@Value("${server.port}")
		int port;

		@Bean
		@Order(5001)
		public GlobalFilter addNonStandardHeaderFilter() {
			return (exchange, chain) -> {
				log.info("addNonStandardHeaderFilter pre phase");
				return chain.filter(exchange).then(Mono.fromRunnable(() -> {
					log.info("addNonStandardHeaderFilter post phase");
					List<String> contentTypes = exchange.getResponse().getHeaders().get(HttpHeaders.CONTENT_TYPE);
					contentTypes.clear();
					contentTypes.add(CONTENT_TYPE_IMAGE);
				}));
			};
		}

		@Bean
		public RouteLocator testRouteLocator(RouteLocatorBuilder builder) {
			return builder.routes()
					.route("non_standard_header_route", r ->
							r.path("/get-image/**")
									.filters(f -> f
											.addRequestHeader(HttpHeaders.HOST, "www.addrequestparameter.org")
											.stripPrefix(1)
									)
									.uri("http://localhost:" + port + "/get"))
					.route("internal_route", r ->
							r.path("/get/**")
									.filters(f -> f.prefixPath("/httpbin"))
									.uri(uri))
					.build();
		}

	}

}
