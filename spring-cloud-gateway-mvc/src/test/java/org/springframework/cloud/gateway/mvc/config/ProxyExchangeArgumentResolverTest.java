/*
 * Copyright 2016-2019 the original author or authors.
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

package org.springframework.cloud.gateway.mvc.config;

import java.net.URI;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.cloud.gateway.mvc.ProxyExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.http.converter.ByteArrayHttpMessageConverter;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.DefaultResponseErrorHandler;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@ContextConfiguration(classes = ProxyExchangeArgumentResolverTest.ProxyExchangeArgumentResolverTestApplication.class)
public class ProxyExchangeArgumentResolverTest {

	@Autowired
	private TestRestTemplate rest;

	@Autowired
	private ProxyExchangeArgumentResolverTestApplication application;

	@LocalServerPort
	private int port;

	@BeforeEach
	public void setUp() throws Exception {
		application.setHome(new URI("http://localhost:" + port));
		rest.getRestTemplate().setRequestFactory(new SimpleClientHttpRequestFactory());
	}

	@Test
	public void shouldProxyRequestWhenProxyExchangeArgumentResolverIsNotConfigured() {
		final ResponseEntity<String> response = rest.getForEntity("/proxy", String.class);
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(response.getBody()).contains("Hello World");
	}

	@SpringBootApplication
	static class ProxyExchangeArgumentResolverTestApplication {

		@Autowired
		private ProxyController controller;

		@Bean
		public ProxyExchangeArgumentResolver proxyExchangeArgumentResolver() {
			RestTemplateBuilder builder = new RestTemplateBuilder();
			builder.errorHandler(new DefaultResponseErrorHandler());
			builder.messageConverters(new ByteArrayHttpMessageConverter());
			return new ProxyExchangeArgumentResolver(builder.build());
		}

		public void setHome(final URI home) {
			controller.setHome(home);
		}

		@RestController
		static class ProxyController {

			private URI home;

			public void setHome(URI home) {
				this.home = home;
			}

			@GetMapping("/proxy")
			public ResponseEntity<?> proxyFoo(ProxyExchange<?> proxy) {
				return proxy.uri(home.toString() + "/foo").get();
			}

		}

		@RestController
		static class TestController {

			@GetMapping("/foo")
			public List<String> foo() {
				return Collections.singletonList("Hello World");
			}

		}

	}

}
