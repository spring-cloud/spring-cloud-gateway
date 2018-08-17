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

package org.springframework.cloud.gateway.filter.factory;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.test.BaseWebClientTests;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.util.StringUtils;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;

import static org.junit.Assert.assertEquals;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = RANDOM_PORT)
@DirtiesContext
public class NonStandardHeadersInResponseTests extends BaseWebClientTests {

	public static final String CONTENT_TYPE_IMAGE = "Content-Type: image";

	@Test
	public void nonStandardHeadersInResponse() {
		URI uri = UriComponentsBuilder.fromUriString(this.baseUri+"/get").build(true).toUri();
		testClient.get()
				.uri(uri)
				.exchange()
				.expectBody(String.class)
				.consumeWith(response -> {
					String contentType = response.getResponseHeaders()
							.get(HttpHeaders.CONTENT_TYPE)
							.stream()
							.filter(s -> StringUtils.hasLength(s))
							.filter(s -> s.equals(CONTENT_TYPE_IMAGE))
							.findFirst()
							.orElseThrow(() -> new RuntimeException("unable to find header"));
					assertEquals(CONTENT_TYPE_IMAGE, contentType);
                });
	}

	@EnableAutoConfiguration
	@SpringBootConfiguration
	@Import(DefaultTestConfig.class)
	public static class TestConfig {
		private static final Log log = LogFactory.getLog(TestConfig.class);

		@Bean
		@Order(501)
		public GlobalFilter nonStandardHeaderInResponseFilter() {
			return (exchange, chain) -> {
				log.info("nonStandardHeaderInResponseFilter start");
				exchange.getResponse().getHeaders().add(HttpHeaders.CONTENT_TYPE, CONTENT_TYPE_IMAGE);
				return chain.filter(exchange);
			};
		}
	}

}
