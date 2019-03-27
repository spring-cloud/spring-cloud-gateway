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
 *
 */

package org.springframework.cloud.gateway.filter.headers;

import org.junit.Test;

import org.junit.runner.RunWith;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.test.BaseWebClientTests;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.web.server.ServerWebExchange;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

/**
 * @author Spencer Gibb
 */
@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = RANDOM_PORT)
@DirtiesContext
public class HttpStatusInResponseHeadersFilterTests extends BaseWebClientTests  {

	@Test
	public void statusCodeAvailableInResponseHttpHeadersFilter() {
		testClient.get()
				.uri("/get")
				.exchange()
				.expectStatus().isOk();
	}

	@EnableAutoConfiguration
	@SpringBootConfiguration
	@Import(DefaultTestConfig.class)
	public static class TestConfig {

		@Bean
		public HttpHeadersFilter checkStatusFilter() {
			return new HttpHeadersFilter() {
				@Override
				public HttpHeaders filter(HttpHeaders input, ServerWebExchange exchange) {
					HttpStatus statusCode = exchange.getResponse().getStatusCode();
					assertThat(statusCode).isEqualTo(HttpStatus.OK);
					return input;
				}

				@Override
				public boolean supports(Type type) {
					return type == Type.RESPONSE;
				}
			};
		}

	}
}
