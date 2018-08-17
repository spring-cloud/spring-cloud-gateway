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

package org.springframework.cloud.gateway.filter.headers;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.cloud.gateway.test.BaseWebClientTests;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;

import static org.junit.Assert.assertEquals;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = RANDOM_PORT)
@DirtiesContext
@ActiveProfiles(profiles = "request-parameter-web-filter")
public class NonStandardHeadersInResponseTests extends BaseWebClientTests {

	public static final String CONTENT_TYPE_IMAGE = "image";

	@Test
	public void nonStandardHeadersInResponse() {
		URI uri = UriComponentsBuilder.fromUriString(this.baseUri+"/get-image/public/event-logo/2016/10-25-07-52-sourcedirect-at-ny-now.gif").build(true).toUri();


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

		@Bean
		public RouteLocator testRouteLocator(RouteLocatorBuilder builder) {
			return builder.routes()
					.route("non_standard_header_test", r ->
							r.path("/get-image/**")
									.filters(f -> f
											.stripPrefix(1)
									)
									.uri("https://s3-prod-otaibe12e86cef1cff.s3.eu-central-1.amazonaws.com"))
					.build();
		}

	}

}
