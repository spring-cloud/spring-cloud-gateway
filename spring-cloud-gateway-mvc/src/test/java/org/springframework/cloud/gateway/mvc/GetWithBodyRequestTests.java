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

package org.springframework.cloud.gateway.mvc;

import java.io.IOException;
import java.net.URI;
import java.util.Collections;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.cloud.gateway.mvc.GetWithBodyRequestTests.TestApplication.Foo;
import org.springframework.cloud.gateway.mvc.config.ProxyExchangeArgumentResolver;
import org.springframework.cloud.gateway.mvc.config.ProxyProperties;
import org.springframework.cloud.gateway.mvc.http.GetWithBodyRequestClientHttpRequestFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.converter.ByteArrayHttpMessageConverter;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.DefaultResponseErrorHandler;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ContextConfiguration(classes = GetWithBodyRequestTests.TestApplication.class)
public class GetWithBodyRequestTests {

	@Autowired
	private TestRestTemplate rest;

	@Autowired
	private TestApplication testApplication;

	@LocalServerPort
	private int port;

	@BeforeEach
	public void init() throws Exception {
		testApplication.setHome(new URI("http://localhost:" + port));
		rest.getRestTemplate().setRequestFactory(new GetWithBodyRequestClientHttpRequestFactory());
	}

	@Test
	public void get() {
		assertThat(rest.getForObject("/proxy/0", Foo.class).getName()).isEqualTo("bye");
	}

	@Test
	public void getWithBodyRequest() {
		final HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);
		headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));

		final Foo bodyRequest = new Foo("hello");
		final HttpEntity<Foo> entity = new HttpEntity<>(bodyRequest, headers);

		final ResponseEntity<Foo> response = rest.exchange("/proxy/get-with-body-request", HttpMethod.GET, entity,
				Foo.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(response.getBody()).isInstanceOfSatisfying(Foo.class,
				foo -> assertThat(foo.getName()).isEqualTo("hello world"));
	}

	@SpringBootApplication
	static class TestApplication {

		@Autowired
		private ProxyController proxyController;

		public void setHome(URI home) {
			proxyController.setHome(home);
		}

		@Bean
		public ProxyExchangeArgumentResolver proxyExchangeArgumentResolver(final ProxyProperties proxy) {
			ProxyExchangeArgumentResolver resolver = new ProxyExchangeArgumentResolver(
					generateConfiguredRestTemplate());
			resolver.setHeaders(proxy.convertHeaders());
			resolver.setAutoForwardedHeaders(proxy.getAutoForward());
			resolver.setSensitive(proxy.getSensitive());
			return resolver;
		}

		private RestTemplate generateConfiguredRestTemplate() {
			final RestTemplateBuilder builder = new RestTemplateBuilder();
			final RestTemplate template = builder.build();

			template.setRequestFactory(new GetWithBodyRequestClientHttpRequestFactory());
			template.setErrorHandler(new NoOpResponseErrorHandler());
			template.getMessageConverters().add(new ByteArrayHttpMessageConverter() {
				@Override
				public boolean supports(Class<?> clazz) {
					return true;
				}
			});

			return template;
		}

		@RestController
		static class ProxyController {

			private URI home;

			public void setHome(URI home) {
				this.home = home;
			}

			@GetMapping("/proxy/{id}")
			public ResponseEntity<?> proxyFoos(@PathVariable Integer id, ProxyExchange<?> proxy) throws Exception {
				return proxy.uri(home.toString() + "/foos/" + id).get();
			}

			@GetMapping("/proxy/get-with-body-request")
			public ResponseEntity<?> proxyFooWithBody(@RequestBody Foo foo, ProxyExchange<?> proxy) throws Exception {
				return proxy.uri(home.toString() + "/foo/get-with-body-request").get();
			}

		}

		@RestController
		static class TestController {

			@GetMapping("/foos/{id}")
			public Foo foo(@PathVariable Integer id, @RequestHeader HttpHeaders headers) {
				String custom = headers.getFirst("X-Custom");
				return new Foo(id == 1 ? "foo" : custom != null ? custom : "bye");
			}

			@GetMapping("/foo/get-with-body-request")
			public Foo getWithBody(@RequestBody Foo foo) {
				return new Foo(foo.getName() + " world");
			}

		}

		@JsonIgnoreProperties(ignoreUnknown = true)
		static class Foo {

			private String name;

			Foo() {
			}

			Foo(String name) {
				this.name = name;
			}

			public String getName() {
				return name;
			}

			public void setName(final String name) {
				this.name = name;
			}

		}

		private static class NoOpResponseErrorHandler extends DefaultResponseErrorHandler {

			@Override
			public void handleError(ClientHttpResponse response) throws IOException {
			}

		}

	}

}
