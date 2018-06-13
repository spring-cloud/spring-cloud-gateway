/*
 * Copyright 2002-2018 the original author or authors.
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
 */

package org.springframework.cloud.gateway.filter.body;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.springframework.http.MediaType.TEXT_EVENT_STREAM;
import static org.springframework.web.reactive.function.BodyInserters.fromServerSentEvents;
import static org.springframework.web.reactive.function.server.RouterFunctions.route;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.cloud.gateway.filter.factory.rewrite.RewriteFunction;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.cloud.gateway.test.PermitAllSecurityConfiguration;
import org.springframework.cloud.gateway.test.support.HttpServer;
import org.springframework.cloud.gateway.test.support.ReactorHttpServer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.ResolvableType;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.http.server.reactive.HttpHandler;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.server.RequestPredicates;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * Tests for modifying response body.
 * 
 * @author Sebastien Deleuze
 * @author Anton Brok-Volchansky
 *
 */
public class ModifyResponseBodyTest {

	protected Log logger = LogFactory.getLog(getClass());

	protected int serverPort;
	public HttpServer server;
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
		logger.info("Server Behind Gateway's Port: "+this.serverPort);

		this.gatewayContext = new SpringApplicationBuilder(GatewayConfig.class)
				.properties("rewritebody.server.port:"+this.serverPort, "server.port=0", "spring.jmx.enabled=false")
				.run();

		ConfigurableEnvironment env = this.gatewayContext.getBean(ConfigurableEnvironment.class);
		this.gatewayPort = new Integer(env.getProperty("local.server.port"));

		this.webClient = WebClient.create("http://localhost:" + this.gatewayPort + "/body-response");

		logger.info("Gateway Port: "+this.gatewayPort);
	}

	public WebClient getWebClient() {
		return this.webClient;
	}
	
	@After
	public void tearDoen() throws Exception {
		this.server.stop();
		this.serverPort = 0;
		this.gatewayPort = 0;
		this.gatewayContext.close();
	}
	
	protected HttpHandler createHttpHandler() {
		RouterFunction<?> routerFunction = routerFunction();
		return RouterFunctions.toHttpHandler(routerFunction); //, HandlerStrategies.withDefaults());
	}
	
	private RouterFunction<?> routerFunction() {
		RewriteResponseBodyHandler handler = new RewriteResponseBodyHandler();
		// @formatter:off
		return 		 route(RequestPredicates.GET("/body-response/string"), handler::string)
				.and(route(RequestPredicates.GET("/body-response/person"), handler::person))
				.and(route(RequestPredicates.GET("/body-response/person2string"), handler::person2string))
				.and(route(RequestPredicates.GET("/body-response/string2person"), handler::string2person))
				.and(route(RequestPredicates.GET("/body-response/ssestring"), handler::sseString))
				.and(route(RequestPredicates.GET("/body-response/sseperson"), handler::ssePerson))
				.and(route(RequestPredicates.GET("/body-response/sseeventstr"), handler::sseEventString))
				.and(route(RequestPredicates.GET("/body-response/sseeventperson"), handler::sseEventPerson));
		// @formatter:on
	}

	
	private static class RewriteResponseBodyHandler {

		private static final Flux<Long> INTERVAL = interval(Duration.ofMillis(100), 2);
		private static final Flux<Person> PERSONS = persons(1000);
		
		Mono<ServerResponse> string(ServerRequest request) {
			return ServerResponse.ok()
					.body(BodyInserters.fromPublisher(Flux.just("foo"), String.class));
		}
		
		Mono<ServerResponse> person(ServerRequest request) {
			return ServerResponse.ok()
					.body(BodyInserters.fromPublisher(Flux.just(new Person("John", "Doe")), Person.class));
		}
		
		Mono<ServerResponse> person2string(ServerRequest request) {
			return person(request);
		}
		
		Mono<ServerResponse> string2person(ServerRequest request) {
			return ServerResponse.ok()
					.body(BodyInserters.fromPublisher(Flux.just("John Doe"), String.class));
		}

		Mono<ServerResponse> sseString(ServerRequest request) {
			return ServerResponse.ok()
					.contentType(MediaType.TEXT_EVENT_STREAM)
					.body(INTERVAL.map(aLong -> "foo " + aLong), String.class);
		}

		Mono<ServerResponse> ssePerson(ServerRequest request) {
			return ServerResponse.ok()
					.contentType(MediaType.TEXT_EVENT_STREAM)
					.body(PERSONS, Person.class);
		}

		Mono<ServerResponse> sseEventString(ServerRequest request) {
			Flux<ServerSentEvent<String>> body = INTERVAL
					.map(l -> ServerSentEvent.builder("foo")
					.id(Long.toString(l))
					.comment("bar")
					.build());
			return ServerResponse.ok().body(fromServerSentEvents(body));
		}
	
		Mono<ServerResponse> sseEventPerson(ServerRequest request) {
			Flux<ServerSentEvent<Person>> body = PERSONS.map(p -> ServerSentEvent.builder(p)
					.id(Integer.toString(p.getFullName().length()))
					.comment(p.toString())
					.build());
			return ServerResponse.ok().body(fromServerSentEvents(body));
		}
	}
	
	
	@Test
	public void stringToString() {
		Flux<String> result = this.getWebClient().get()
				.uri("/string")
				.accept(MediaType.TEXT_PLAIN)
				.exchange()
				.flatMapMany(response -> response.bodyToFlux(String.class));

		StepVerifier.create(result)
			.expectNext("foo bar")
			.expectComplete()
			.verify(Duration.ofSeconds(5L))
			;
	}
	
	@Test
	public void jsonToJson() {
		Flux<Person> result = this.getWebClient().get()
			.uri("/person")
			.accept(MediaType.APPLICATION_JSON)
			.exchange()
			.flatMapMany(response -> response.bodyToFlux(Person.class))
			;

		StepVerifier.create(result)
			.expectNext(new Person("John","Smith"))
			.expectComplete()
			.verify(Duration.ofSeconds(5L));
	}

	@Test
	public void jsonToString() {
		Flux<String> result = this.getWebClient().get()
			.uri("/person2string")
			.accept(MediaType.APPLICATION_JSON)
			.exchange()
			.flatMapMany(response -> response.bodyToFlux(String.class))
			;

		StepVerifier.create(result)
			.expectNext("John Doe")
			.expectComplete()
			.verify(Duration.ofSeconds(5555L));
	}

	@Test
	public void stringToJson() {
		Flux<Person> result = this.getWebClient().get()
			.uri("/string2person")
			.accept(MediaType.APPLICATION_JSON)
			.retrieve()
			.bodyToFlux(new ParameterizedTypeReference<Person>() {})
			;

		StepVerifier.create(result)
			.expectNext(new Person("John", "Doe"))
			.expectComplete()
			.verify(Duration.ofSeconds(5555L));
	}

	@Test
	public void sseAsString() {
		Flux<String> result = this.getWebClient().get()
				.uri("/ssestring")
				.accept(TEXT_EVENT_STREAM)
				.exchange()
				.flatMapMany(response -> response.bodyToFlux(String.class));

		StepVerifier.create(result)
				.expectNext("bar 0")
				.expectNext("bar 1")
				.expectComplete()
				.verify(Duration.ofSeconds(55555L));
	}
	
	@Test
	public void sseAsJson() {
		Flux<Person> result = this.getWebClient().get()
				.uri("/sseperson")
				.accept(TEXT_EVENT_STREAM)
				.exchange()
				.flatMapMany(response -> response.bodyToFlux(Person.class));

		StepVerifier.create(result)
				.expectNext(new Person("Jonathan", "Smith"))
				.expectNext(new Person("Jonathan", "Doe"))
				.expectComplete()
				.verify(Duration.ofSeconds(5L));
	}
	
	@Test
	public void sseAsEventOfStrings() {
		Flux<ServerSentEvent<String>> result = this.getWebClient().get()
				.uri("/sseeventstr")
				.accept(TEXT_EVENT_STREAM)
				.retrieve()
				.bodyToFlux(new ParameterizedTypeReference<ServerSentEvent<String>>() {});

		StepVerifier.create(result)
				.consumeNextWith( event -> {
					assertEquals("0", event.id());
					assertEquals("bar", event.data());
					assertEquals("bar", event.comment());
					assertNull(event.event());
					assertNull(event.retry());
				})
				.consumeNextWith( event -> {
					assertEquals("1", event.id());
					assertEquals("bar", event.data());
					assertEquals("bar", event.comment());
					assertNull(event.event());
					assertNull(event.retry());
				})
				.expectComplete()
				.verify(Duration.ofSeconds(555L));
	}

	@Test
	public void sseAsEventOfJson() {
		Flux<ServerSentEvent<Person>> result = this.getWebClient().get()
				.uri("/sseeventperson")
				.accept(TEXT_EVENT_STREAM)
				.retrieve()
				.bodyToFlux(new ParameterizedTypeReference<ServerSentEvent<Person>>() {});
				;

		StepVerifier.create(result)
				.consumeNextWith( event -> {
					assertEquals("1", event.id());
					assertEquals(new Person("Jonathan", "Smith"), event.data());
					assertNull(event.event());
					assertNull(event.retry());
				})
				.consumeNextWith( event -> {
					assertEquals("2", event.id());
					assertEquals(new Person("Jonathan", "Doe"), event.data());
					assertNull(event.event());
					assertNull(event.retry());
				})
				.expectComplete()
				.verify(Duration.ofSeconds(5555L));
	}

	public static Flux<Long> interval(Duration period, int count) {
		return Flux.interval(period).take(count).onBackpressureBuffer(2);
	}
	
	public static Flux<Person> persons(int count) {
		List<Person> persons = new ArrayList<>();
		persons.add(new Person("John", "Smith"));
		persons.add(new Person("John","Doe"));
		return Flux.fromIterable(persons);
	}
	
	

	@Configuration
	@EnableAutoConfiguration
	@Import(PermitAllSecurityConfiguration.class)
	protected static class GatewayConfig {

		@Value("${rewritebody.server.port}")
		private int port;

		@SuppressWarnings("unchecked")
		@Bean
		public RouteLocator rewriteResponseRouteLocator(RouteLocatorBuilder builder) {
			
			RewriteFunction<Person, Person> personToPersonRewriteFn = (exchange, originalBody) -> {
				
				return new Person(originalBody.getFirstName(), "Smith");
			};
			
			String uri = "http://localhost:" + this.port;
			
			AtomicInteger index = new AtomicInteger(1);
			
			ResolvableType genericIn = ResolvableType.forClassWithGenerics(ServerSentEvent.class, Person.class);
			ResolvableType genericOut = ResolvableType.forClassWithGenerics(ServerSentEvent.class, Person.class);
			
			// @formatter:off
			return builder.routes()
					
					.route("ResponseBodyRewrite_StringToString_Route", r ->
						r.path("/body-response/string")
						.filters(f ->
							f.modifyResponseBody(c ->
								c.setInClass(String.class)
								.setOutClass(String.class)
								.setOutMediaType(MediaType.TEXT_PLAIN)
								.setRewriteFunction((exchange, originalBody) -> {
									return originalBody + " " + "bar";
								})
							)
						)
						.uri(uri)
					)
					
					.route("ResponseBodyRewrite_JsonToJson_Route", r ->
						r.path("/body-response/person")
						.filters(f ->
							f.<Person,Person>modifyResponseBody(c ->
								c.setInClass(Person.class)
								.setOutClass(Person.class)
								.setOutMediaType(MediaType.APPLICATION_JSON)
								.setRewriteFunction(personToPersonRewriteFn)
							)
						)
						.uri(uri)
					)
					
					.route("ResponseBodyRewrite_JsonToString_Route", r ->
						r.path("/body-response/person2string")
						.filters(f ->
							f.<Person,String>modifyResponseBody(c ->
								c.setInClass(Person.class)
								.setOutClass(String.class)
								.setOutMediaType(MediaType.TEXT_PLAIN)
								.setRewriteFunction((exchange, originalBody) -> {
									return originalBody.getFullName();
								})
							)
						)
						.uri(uri)
					)
					
					.route("ResponseBodyRewrite_StringToJson_Route", r ->
						r.path("/body-response/string2person")
						.filters(f ->
							f.<String,Person>modifyResponseBody(c ->
								c.setInClass(String.class)
								.setOutClass(Person.class)
								.setOutMediaType(MediaType.APPLICATION_JSON)
								.setRewriteFunction((exchange, originalBody) -> {
									String[] nameParts = originalBody.split("\\s+");
									return new Person(nameParts[0], nameParts[1]);
								})
							)
						)
						.uri(uri)
					)
					
					.route("ResponseBodyRewrite_Sse_String_Route", r ->
						r.path("/body-response/ssestring")
						.filters(f ->
							f.modifyResponseBody(c ->
								c.setInClass(String.class)
								.setOutClass(String.class)
								.setOutMediaType(MediaType.TEXT_EVENT_STREAM)
								.setRewriteFunction((exchange, originalBody) -> {
									System.out.println("class of originalBody: " + originalBody.getClass().getName());
									return originalBody.toString().replace("foo", "bar");
								})
							)
						)
						.uri(uri)
					)
					
					
					.route("ResponseBodyRewrite_Sse_Json_Route", r ->
						r.path("/body-response/sseperson")
						.filters(f ->
							f.modifyResponseBody(c ->
								c.setInClass(Person.class)
								.setOutClass(Person.class)
								.setOutMediaType(MediaType.TEXT_EVENT_STREAM)
								.setRewriteFunction((exchange, originalBody) -> {
									Person changed = new Person("Jonathan", ((Person)originalBody).getLastName());
									return changed;
								})
							)
						)
						.uri(uri)
					)
					
					.route("ResponseBodyRewrite_Sse_Event_String_Route", r ->
						r.path("/body-response/sseeventstr")
						.filters(f ->
							f.modifyResponseBody(c ->
								c.setInClass(ResolvableType.forClassWithGenerics(ServerSentEvent.class, String.class))
								.setOutClass(ResolvableType.forClassWithGenerics(ServerSentEvent.class, String.class))
								.setOutMediaType(MediaType.TEXT_EVENT_STREAM)
								.setRewriteFunction((exchange, originalBody) -> {
									
									ServerSentEvent<String> origServerSentEvent = (ServerSentEvent<String>) originalBody;
									
									String oldBody = origServerSentEvent.data();
									String newBody = oldBody.replace("foo", "bar");
									
									ServerSentEvent<String> changed = ServerSentEvent
											.<String>builder(newBody)
											.id(origServerSentEvent.id())
											.comment(newBody)
											.build()
											;
									return changed;
								})
							)
						)
						.uri(uri)
					)
						
					
					.route("ResponseBodyRewrite_Sse_Event_Json_Route", r ->
						r.path("/body-response/sseeventperson")
						.filters(f ->
							f.modifyResponseBody(c ->
								c.setInClass(genericIn)
								.setOutClass(genericOut)
								.setOutMediaType(MediaType.TEXT_EVENT_STREAM)
								.setRewriteFunction((exchange, originalBody) -> {
									
									ServerSentEvent<Person> bodyIn = (ServerSentEvent<Person>) originalBody;
									
									String idx = Integer.toString(index.getAndIncrement());
									Person newPerson = new Person("Jonathan", bodyIn.data().getLastName());

									ServerSentEvent<Person> changed = ServerSentEvent
											.<Person>builder(newPerson)
											.id(idx)
											.comment(bodyIn.comment())
											.build()
											;
									return changed;
								})
							)
						)
						.uri(uri)
					)
					
					
				.build();
			// @formatter:on
		}
	}
}
