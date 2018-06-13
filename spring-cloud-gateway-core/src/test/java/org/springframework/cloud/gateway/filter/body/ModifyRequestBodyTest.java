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

import java.time.Duration;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
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
import org.springframework.core.ResolvableType;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.HttpHandler;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.DispatcherHandler;
import org.springframework.web.reactive.config.EnableWebFlux;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.adapter.WebHttpHandlerBuilder;

import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * Tests for modifying request body.
 * 
 * @author Sebastien Deleuze
 * @author Anton Brok-Volchansky
 *
 */
public class ModifyRequestBodyTest {
	
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
		logger.info("Server Behind Gateway's Port: "+this.serverPort);

		this.gatewayContext = new SpringApplicationBuilder(GatewayConfig.class)
				.properties("requestbodyrewrite.server.port:"+this.serverPort, "server.port=0", "spring.jmx.enabled=false")
				.run();

		ConfigurableEnvironment env = this.gatewayContext.getBean(ConfigurableEnvironment.class);
		this.gatewayPort = new Integer(env.getProperty("local.server.port"));

		this.webClient = WebClient.builder()
				.baseUrl("http://localhost:" + this.gatewayPort + "/rewrite-request-body")
				.build()
				;

		logger.info("Gateway Port: "+this.gatewayPort);
	}

	@After
	public void tearDoen() throws Exception {
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
		
		DispatcherHandler webHandler = new DispatcherHandler(this.wac);
		return WebHttpHandlerBuilder.webHandler(webHandler)
				.build();
	}

	@Test
	public void stringToString() {
		Mono<String> result = this.webClient.post()
				.uri("/post-string-string")
				.body(BodyInserters.fromPublisher(Mono.just("foo"), String.class))
				.accept(MediaType.TEXT_PLAIN)
				.retrieve()
				.bodyToMono(String.class);

		StepVerifier.create(result)
			.expectNext("bar")
			.expectComplete()
			.verify(Duration.ofSeconds(555555L));
	}
	
	@Test
	public void stringToJson() {
		Mono<Person> result = this.webClient.post()
				.uri("/post-string-person")
				.accept(MediaType.APPLICATION_JSON)
				.body(BodyInserters.fromPublisher(Mono.just("John"), String.class))
				.retrieve()
				.bodyToMono(Person.class);

		StepVerifier.create(result)
			.expectNext(new Person("John", "Smith"))
			.expectComplete()
			.verify(Duration.ofSeconds(55555L));
	}
	
	@Test
	public void formDataToJson() {
		MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
		formData.add("user", "John");
		formData.add("password", "qwerty");
		
		Mono<Login> result = this.webClient.post()
				.uri("/post-formdata-to-json")
				.body(BodyInserters.fromFormData(formData))
				.retrieve()
				.bodyToMono(Login.class);

		StepVerifier.create(result)
			.expectNext(new Login("John", "qwerty"))
			.expectComplete()
			.verify(Duration.ofSeconds(5L));
	}
	
	@Test
	public void jsonToJson() {
		Login login = new Login("John", "Doe");
		
		Mono<Person> result = this.webClient.post()
				.uri("/post-json-to-json")
				.accept(MediaType.APPLICATION_JSON)
				.body(BodyInserters.fromObject(login))
				.retrieve()
				.bodyToMono(Person.class);

		StepVerifier.create(result)
		.expectNext(new Person("John","Smith"))
		.expectComplete()
		.verify()
		;
	}
	
	@Test
	public void jsonToFormData() throws Exception {
		Person person = new Person("John", "Doe");
		
		Mono<String> result = this.webClient.post()
				.uri("/post-json-to-formdata")
				.accept(MediaType.APPLICATION_JSON)
				.body(BodyInserters.fromObject(person))
				.retrieve()
				.bodyToMono(String.class)
				;
		
		MultiValueMap<String, String> map = new LinkedMultiValueMap<>();
		map.add("first", "John");
		map.add("last", "Doe");
		
		StepVerifier.create(result)
		.expectNext(map.toString())
		.expectComplete()
		.verify()
		;
	}
	
	@Test
	public void formDataToFormData() {
		MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
		formData.add("user", "John");
		formData.add("password", "qwerty");
		
		Mono<String> result = this.webClient.post()
				.uri("/post-formdata-to-formdata")
				.accept(MediaType.APPLICATION_FORM_URLENCODED)
				.contentType(MediaType.APPLICATION_FORM_URLENCODED)
				.body(BodyInserters.fromFormData(formData))
				.retrieve()
				.bodyToMono(String.class);
		
		MultiValueMap<String, Object> map = new LinkedMultiValueMap<>();
		map.add("firstName", "John");
		map.add("lastName", "Doe");
		
		StepVerifier.create(result)
		.expectNext(map.toString())
		.expectComplete()
		.verify()
		;
		;
	}
	
	@Test
	public void formDataToFormDataResolvableType() {
		MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
		formData.add("user", "John");
		formData.add("password", "qwerty");
		
		Mono<String> result = this.webClient.post()
				.uri("/post-formdata-to-formdata-resolvable-type")
				.accept(MediaType.APPLICATION_FORM_URLENCODED)
				.contentType(MediaType.APPLICATION_FORM_URLENCODED)
				.body(BodyInserters.fromFormData(formData))
				.retrieve()
				.bodyToMono(String.class);
		
		MultiValueMap<String, Object> map = new LinkedMultiValueMap<>();
		map.add("firstName", "John");
		map.add("lastName", "Doe");
		
		StepVerifier.create(result)
		.expectNext(map.toString())
		.expectComplete()
		.verify()
		;
		;
	}

	@RestController
	static class BodyResponseRewriteController {
		
		@PostMapping(value="/rewrite-request-body/post-string-string")
		Mono<String> post(@RequestBody String foo) {
	        return Mono.just(foo);
	    }
		
		@PostMapping(path = "/rewrite-request-body/post-string-person")
		Mono<Person> post(@RequestBody Mono<Person> person) {
			return person;
	    }

		@PostMapping(path = "/rewrite-request-body/post-formdata-to-json")
		Mono<Login> formData2Json(@RequestBody Mono<Login> login) {
			return login;
		}
		
		@PostMapping(path="/rewrite-request-body/post-json-to-json")
		Mono<Person> jsonToJson(@RequestBody Mono<Person> person) {
			return person;
		}
		
		@PostMapping(path="/rewrite-request-body/post-json-to-formdata")
		Mono<String> jsonToFormData(@RequestBody Mono<MultiValueMap<String, String>> map) {
			return map
					.cache()
					.flatMap(inValue -> Mono.just(inValue.toString()) );
		}
		
		@PostMapping(path="/rewrite-request-body/post-formdata-to-formdata")
		Mono<String> formDataToFormData(@RequestBody Mono<MultiValueMap<String, String>> map) {
			return map.flatMap(m -> Mono.just(m.toString()) );
		}
		
		@PostMapping(path="/rewrite-request-body/post-formdata-to-formdata-resolvable-type")
		Mono<String> formDataToFormDataResolvableType(@RequestBody Mono<MultiValueMap<String, String>> map) {
			return formDataToFormData(map);
		}
	}

	@Configuration
	@EnableWebFlux
	static class TestConfiguration {
		
		@Bean
		public BodyResponseRewriteController controller() {
			return new BodyResponseRewriteController();
		}
	}
	
	@Configuration
	@EnableAutoConfiguration
	@Import(PermitAllSecurityConfiguration.class)
	protected static class GatewayConfig {

		@Value("${requestbodyrewrite.server.port}")
		private int port;

		@SuppressWarnings("unchecked")
		@Bean
		public RouteLocator rewriteRequestRouteLocator(RouteLocatorBuilder builder) {
			ResolvableType genericIn = ResolvableType.forClassWithGenerics(MultiValueMap.class, String.class, String.class);
			ResolvableType genericOut = ResolvableType.forClassWithGenerics(MultiValueMap.class, String.class, String.class);
			
			// @formatter:off
			return builder.routes()
					
					.route("RequestBodyRewrite_StringToString_Route", r ->
						r.path("/rewrite-request-body/post-string-string")
						.filters(f ->
							f.modifyRequestBody(c ->
								c.setInClass(String.class)
								.setOutClass(String.class)
								.setRewriteFunction((exchange, originalBody) -> {
									System.out.println("originalBody:" + originalBody);
									return "bar";
								})
								.setOutMediaType(MediaType.TEXT_PLAIN)
								
							)
						).uri("http://localhost:"+this.port)
					)
					
					.route("RequestBodyRewrite_StringToPerson_Route", r ->
						r.path("/rewrite-request-body/post-string-person")
						.filters(f ->
							f.modifyRequestBody(c ->
								c.setInClass(String.class)
								.setOutClass(Person.class)
								.setRewriteFunction((exchange, originalBody) -> {
									System.out.println("originalBody:" + originalBody);
									return new Person(originalBody.toString(), "Smith");
								})
								.setOutMediaType(MediaType.APPLICATION_JSON)
							)
						).uri("http://localhost:"+this.port)
					)
					
					.route("RequestBodyRewrite_formDataToJson_Route", r ->
						r.path("/rewrite-request-body/post-formdata-to-json")
						.filters(f ->
							f.modifyRequestBody(c ->
								c.setInClass(MultiValueMap.class)
								.setOutClass(Login.class)
								.setRewriteFunction((exchange, originalBody) -> {
									Map<String, String> map = ((MultiValueMap<String, String>)originalBody).toSingleValueMap();
									System.out.println("originalBody - mapped to <String,String>:" + map.toString());
									
									String user = map.get("user");
									String password = map.get("password");
									
									return new Login(user, password);
								})
								.setOutMediaType(MediaType.APPLICATION_JSON)
							)
						).uri("http://localhost:"+this.port)
					)
					
					.route("RequestBodyRewrite_jsonToJson_Route", r ->
						r.path("/rewrite-request-body/post-json-to-json")
						.filters(f ->
							f.modifyRequestBody(c ->
								c.setInClass(Login.class)
								.setOutClass(Person.class)
								.setRewriteFunction((exchange, originalBody) -> {
									System.out.println("originalBody: " + originalBody.toString());
									
									return new Person(((Login)originalBody).getUser(), "Smith");
								})
								.setOutMediaType(MediaType.APPLICATION_JSON)
							)
						).uri("http://localhost:"+this.port)
					)
					
					.route("RequestBodyRewrite_jsonToFormData_Route", r ->
						r.path("/rewrite-request-body/post-json-to-formdata")
						.filters(f ->
							f.modifyRequestBody(c ->
								c.setInClass(Person.class)
								.setOutClass(MultiValueMap.class)
								.setRewriteFunction((exchange, originalBody) -> {
									MultiValueMap<String, Object> map = new LinkedMultiValueMap<>();
									map.add("first", "John");
									map.add("last", "Doe");
									return map;
								})
								.setOutMediaType(MediaType.APPLICATION_FORM_URLENCODED)
							)
						).uri("http://localhost:"+this.port)
					)
					
					.route("RequestBodyRewrite_formDataToFormData_Route", r ->
						r.path("/rewrite-request-body/post-formdata-to-formdata")
						.filters(f ->
							f.modifyRequestBody(c ->
								c.setInClass(MultiValueMap.class)
								.setOutClass(MultiValueMap.class)
								.setRewriteFunction((exchange, originalBody) -> {
									MultiValueMap<String, Object> map = new LinkedMultiValueMap<>();
									map.add("firstName", "John");
									map.add("lastName", "Doe");
									return map;
								})
								.setOutMediaType(MediaType.APPLICATION_FORM_URLENCODED)
							)
						).uri("http://localhost:"+this.port)
					)
					
					.route("RequestBodyRewrite_formDataToFormData_Route", r ->
						r.path("/rewrite-request-body/post-formdata-to-formdata-resolvable-type")
						.filters(f ->
							f.modifyRequestBody(c ->
								c.setInClass(genericIn)
								.setOutClass(genericOut)
								.setRewriteFunction((exchange, originalBody) -> {
									MultiValueMap<String, Object> map = new LinkedMultiValueMap<>();
									map.add("firstName", "John");
									map.add("lastName", "Doe");
									return map;
								})
								.setOutMediaType(MediaType.APPLICATION_FORM_URLENCODED)
							)
						).uri("http://localhost:"+this.port)
					)
 
					.build();
			
			// @formatter:on
		}
	}
}
