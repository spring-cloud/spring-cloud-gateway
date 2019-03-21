/*
 * Copyright 2013-2018 the original author or authors.
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

package org.springframework.cloud.gateway.test.sse;

import java.time.Duration;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.cloud.gateway.test.PermitAllSecurityConfiguration;
import org.springframework.cloud.gateway.test.support.HttpServer;
import org.springframework.cloud.gateway.test.support.ReactorHttpServer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.ResolvableType;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.http.server.reactive.HttpHandler;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.DispatcherHandler;
import org.springframework.web.reactive.config.EnableWebFlux;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.adapter.WebHttpHandlerBuilder;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.springframework.core.ResolvableType.forClassWithGenerics;
import static org.springframework.http.MediaType.TEXT_EVENT_STREAM;
import static org.springframework.web.reactive.function.BodyExtractors.toFlux;

/**
 * @author Sebastien Deleuze
 */
public class SseIntegrationTests {

	protected Log logger = LogFactory.getLog(getClass());

	protected int serverPort;

	public HttpServer server;

	private AnnotationConfigApplicationContext wac;

	private WebClient webClient;

	private ConfigurableApplicationContext gatewayContext;

	private int gatewayPort;

	@Before
	public void setup() throws Exception {
		this.server = new ReactorHttpServer();
		this.server.setHandler(createHttpHandler());
		this.server.afterPropertiesSet();
		this.server.start();

		// Set dynamically chosen port
		this.serverPort = this.server.getPort();
		logger.info("SSE Port: "+this.serverPort);


		this.gatewayContext = new SpringApplicationBuilder(GatewayConfig.class)
				.properties("sse.server.port:"+this.serverPort, "server.port=0", "spring.jmx.enabled=false")
				.run();

		ConfigurableEnvironment env = this.gatewayContext.getBean(ConfigurableEnvironment.class);
		this.gatewayPort = new Integer(env.getProperty("local.server.port"));

		this.webClient = WebClient.create("http://localhost:" + this.gatewayPort + "/sse");

		logger.info("Gateway Port: "+this.gatewayPort);
	}

	@After
	public void tearDown() throws Exception {
		this.server.stop();
		this.serverPort = 0;
		this.gatewayPort = 0;
		this.gatewayContext.close();
		this.wac.close();
	}

	private HttpHandler createHttpHandler() {
		this.wac = new AnnotationConfigApplicationContext();
		this.wac.register(TestConfiguration.class);
		this.wac.refresh();

		return WebHttpHandlerBuilder.webHandler(new DispatcherHandler(this.wac)).build();
	}

	@Test
	public void sseAsString() {
		Flux<String> result = this.webClient.get()
				.uri("/string")
				.accept(TEXT_EVENT_STREAM)
				.exchange()
				.flatMapMany(response -> response.bodyToFlux(String.class));

		StepVerifier.create(result)
				.expectNext("foo 0")
				.expectNext("foo 1")
				.thenCancel()
				.verify(Duration.ofSeconds(5L));
	}

	@Test
	public void sseAsPerson() {
		Flux<Person> result = this.webClient.get()
				.uri("/person")
				.accept(TEXT_EVENT_STREAM)
				.exchange()
				.flatMapMany(response -> response.bodyToFlux(Person.class));

		StepVerifier.create(result)
				.expectNext(new Person("foo 0"))
				.expectNext(new Person("foo 1"))
				.thenCancel()
				.verify(Duration.ofSeconds(5L));
	}

	@Test
	@SuppressWarnings("Duplicates")
	public void sseAsEvent() {
		ResolvableType type = forClassWithGenerics(ServerSentEvent.class, String.class);
		Flux<ServerSentEvent<String>> result = this.webClient.get()
				.uri("/event")
				.accept(TEXT_EVENT_STREAM)
				.exchange()
				.flatMapMany(response -> response.body(
						toFlux(new ParameterizedTypeReference<ServerSentEvent<String>>() {})));

		StepVerifier.create(result)
				.consumeNextWith( event -> {
					assertEquals("0", event.id());
					assertEquals("foo", event.data());
					assertEquals("bar", event.comment());
					assertNull(event.event());
					assertNull(event.retry());
				})
				.consumeNextWith( event -> {
					assertEquals("1", event.id());
					assertEquals("foo", event.data());
					assertEquals("bar", event.comment());
					assertNull(event.event());
					assertNull(event.retry());
				})
				.thenCancel()
				.verify(Duration.ofSeconds(5L));
	}

	@Test
	@SuppressWarnings("Duplicates")
	public void sseAsEventWithoutAcceptHeader() {
		Flux<ServerSentEvent<String>> result = this.webClient.get()
				.uri("/event")
				.accept(TEXT_EVENT_STREAM)
				.exchange()
				.flatMapMany(response -> response.body(
						toFlux(new ParameterizedTypeReference<ServerSentEvent<String>>() {})));

		StepVerifier.create(result)
				.consumeNextWith( event -> {
					assertEquals("0", event.id());
					assertEquals("foo", event.data());
					assertEquals("bar", event.comment());
					assertNull(event.event());
					assertNull(event.retry());
				})
				.consumeNextWith( event -> {
					assertEquals("1", event.id());
					assertEquals("foo", event.data());
					assertEquals("bar", event.comment());
					assertNull(event.event());
					assertNull(event.retry());
				})
				.thenCancel()
				.verify(Duration.ofSeconds(5L));
	}

	/**
	 * Return an interval stream of with n number of ticks and buffer the
	 * emissions to avoid back pressure failures (e.g. on slow CI server).
	 */
	public static Flux<Long> interval(Duration period, int count) {
		return Flux.interval(period).take(count).onBackpressureBuffer(2);
	}

	@RestController
	@SuppressWarnings("unused")
	static class SseController {

		private static final Flux<Long> INTERVAL = interval(Duration.ofMillis(100), 50);


		@RequestMapping("/sse/string")
		Flux<String> string() {
			return INTERVAL.map(l -> "foo " + l);
		}

		@RequestMapping("/sse/person")
		Flux<Person> person() {
			return INTERVAL.map(l -> new Person("foo " + l));
		}

		@RequestMapping("/sse/event")
		Flux<ServerSentEvent<String>> sse() {
			return INTERVAL.map(l -> ServerSentEvent.builder("foo")
					.id(Long.toString(l))
					.comment("bar")
					.build());
		}

	}


	@Configuration
	@EnableWebFlux
	@SuppressWarnings("unused")
	static class TestConfiguration {

		@Bean
		public SseController sseController() {
			return new SseController();
		}
	}

	@Configuration
	@EnableAutoConfiguration
	@Import(PermitAllSecurityConfiguration.class)
	protected static class GatewayConfig {

		@Value("${sse.server.port}")
		private int port;

		@Bean
		public RouteLocator sseRouteLocator(RouteLocatorBuilder builder) {
			return builder.routes()
					.route("sse_route", r -> r.alwaysTrue()
							.uri("http://localhost:"+this.port))
					.build();
		}
	}


	@SuppressWarnings("unused")
	private static class Person {

		private String name;

		public Person() {
		}

		public Person(String name) {
			this.name = name;
		}

		public String getName() {
			return name;
		}

		public void setName(String name) {
			this.name = name;
		}

		@Override
		public boolean equals(Object o) {
			if (this == o) {
				return true;
			}
			if (o == null || getClass() != o.getClass()) {
				return false;
			}
			Person person = (Person) o;
			return !(this.name != null ? !this.name.equals(person.name) : person.name != null);
		}

		@Override
		public int hashCode() {
			return this.name != null ? this.name.hashCode() : 0;
		}

		@Override
		public String toString() {
			return "Person{name='" + this.name + '\'' + '}';
		}
	}

}