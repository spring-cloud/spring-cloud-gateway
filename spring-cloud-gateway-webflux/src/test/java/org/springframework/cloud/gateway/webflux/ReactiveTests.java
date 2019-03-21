/*
 * Copyright 2016-2017 the original author or authors.
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

package org.springframework.cloud.gateway.webflux;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import org.junit.Test;
import org.junit.runner.RunWith;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.cloud.gateway.webflux.ReactiveTests.TestApplication;
import org.springframework.cloud.gateway.webflux.ReactiveTests.TestApplication.Bar;
import org.springframework.cloud.gateway.webflux.ReactiveTests.TestApplication.Foo;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.util.ObjectUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.DispatcherHandler;
import org.springframework.web.server.ServerWebExchange;

import static org.assertj.core.api.Assertions.assertThat;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@ContextConfiguration(classes = TestApplication.class)
public class ReactiveTests {

	@Autowired
	private TestRestTemplate rest;

	@LocalServerPort
	private int port;

	@Test
	public void postBytes() throws Exception {
		ResponseEntity<List<Foo>> result = rest.exchange(
				RequestEntity.post(
						rest.getRestTemplate().getUriTemplateHandler().expand("/bytes"))
						.body("hello foo".getBytes()),
				new ParameterizedTypeReference<List<Foo>>() {
				});
		assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(result.getBody().iterator().next().getName()).isEqualTo("hello foo");
	}

	@Test
	public void post() throws Exception {
		ResponseEntity<List<Bar>> result = rest.exchange(
				RequestEntity
						.post(rest.getRestTemplate().getUriTemplateHandler()
								.expand("/bars"))
						.body(Collections
								.singletonList(Collections.singletonMap("name", "foo"))),
				new ParameterizedTypeReference<List<Bar>>() {
				});
		assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(result.getBody().iterator().next().getName()).isEqualTo("hello foo");
	}

	@Test
	public void postFlux() throws Exception {
		ResponseEntity<List<Bar>> result = rest.exchange(
				RequestEntity
						.post(rest.getRestTemplate().getUriTemplateHandler()
								.expand("/flux/bars"))
						.body(Collections
								.singletonList(Collections.singletonMap("name", "foo"))),
				new ParameterizedTypeReference<List<Bar>>() {
				});
		assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(result.getBody().iterator().next().getName()).isEqualTo("hello foo");
	}

	@Test
	public void get() throws Exception {
		ResponseEntity<List<Foo>> result = rest.exchange(RequestEntity
				.get(rest.getRestTemplate().getUriTemplateHandler().expand("/foos"))
				.build(), new ParameterizedTypeReference<List<Foo>>() {
				});
		assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(result.getBody().iterator().next().getName()).isEqualTo("hello");
	}

	@Test
	public void forward() throws Exception {
		ResponseEntity<List<Foo>> result = rest.exchange(
				RequestEntity.get(rest.getRestTemplate().getUriTemplateHandler()
						.expand("/forward/foos")).build(),
				new ParameterizedTypeReference<List<Foo>>() {
				});
		assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(result.getBody().iterator().next().getName()).isEqualTo("hello");
	}

	@SpringBootApplication
	static class TestApplication {

		@RestController
		static class TestController {

			@Autowired
			private DispatcherHandler handler;

			@PostMapping("/bars")
			public List<Bar> bars(@RequestBody List<Foo> foos,
					@RequestHeader HttpHeaders headers) {
				String custom = "hello ";
				return foos.stream().map(foo -> new Bar(custom + foo.getName()))
						.collect(Collectors.toList());
			}

			@PostMapping("/flux/bars")
			public Flux<Bar> fluxbars(@RequestBody Flux<Foo> foos,
					@RequestHeader HttpHeaders headers) {
				String custom = "hello ";
				return foos.map(foo -> new Bar(custom + foo.getName()));
			}

			@GetMapping("/foos")
			public Flux<Foo> foos() {
				return Flux.just(new Foo("hello"));
			}

			@GetMapping("/forward/foos")
			public Mono<Void> forwardFoos(ServerWebExchange exchange) {
				return handler.handle(exchange.mutate()
						.request(request -> request.path("/foos").build()).build());
			}

			@PostMapping("/bytes")
			public Flux<Foo> forwardBars(@RequestBody Flux<byte[]> body) {
				return Flux.from(body.reduce(this::concatenate)
						.map(value -> new Foo(new String(value))));
			}

			byte[] concatenate(@Nullable byte[] array1, @Nullable byte[] array2) {
				if (ObjectUtils.isEmpty(array1)) {
					return array2;
				}
				if (ObjectUtils.isEmpty(array2)) {
					return array1;
				}

				byte[] newArr = new byte[array1.length + array2.length];
				System.arraycopy(array1, 0, newArr, 0, array1.length);
				System.arraycopy(array2, 0, newArr, array1.length, array2.length);
				return newArr;
			}

		}

		@JsonIgnoreProperties(ignoreUnknown = true)
		static class Foo {
			private String name;

			public Foo() {
			}

			public Foo(String name) {
				this.name = name;
			}

			public String getName() {
				return name;
			}

			public void setName(String name) {
				this.name = name;
			}
		}

		@JsonIgnoreProperties(ignoreUnknown = true)
		static class Bar {
			private String name;

			public Bar() {
			}

			public Bar(String name) {
				this.name = name;
			}

			public String getName() {
				return name;
			}

			public void setName(String name) {
				this.name = name;
			}
		}

	}

}