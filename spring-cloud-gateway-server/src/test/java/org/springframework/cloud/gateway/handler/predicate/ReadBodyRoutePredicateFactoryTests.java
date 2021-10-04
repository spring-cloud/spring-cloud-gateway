/*
 * Copyright 2013-2020 the original author or authors.
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

package org.springframework.cloud.gateway.handler.predicate;

import java.util.function.Predicate;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.handler.AsyncPredicate;
import org.springframework.cloud.gateway.handler.predicate.ReadBodyRoutePredicateFactory.Config;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.cloud.gateway.test.BaseWebClientTests.TestLoadBalancerConfig;
import org.springframework.cloud.gateway.test.PermitAllSecurityConfiguration;
import org.springframework.cloud.loadbalancer.annotation.LoadBalancerClient;
import org.springframework.cloud.loadbalancer.annotation.LoadBalancerClients;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.server.ServerWebExchange;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

/**
 * @author Ryan Baxter
 */
@SpringBootTest(webEnvironment = RANDOM_PORT)
@DirtiesContext
public class ReadBodyRoutePredicateFactoryTests {

	@Autowired
	private WebTestClient webClient;

	@Test
	public void readBodyWorks() {

		Event messageEvent = new Event("message", "bar");
		Event messageChannelEvent = new Event("message.channels", "bar");

		webClient.post().uri("/events").body(BodyInserters.fromValue(messageEvent)).exchange().expectStatus().isOk()
				.expectBody().jsonPath("$.headers.Hello").isEqualTo("World");

		webClient.post().uri("/events").body(BodyInserters.fromValue(messageChannelEvent)).exchange().expectStatus()
				.isOk().expectBody().jsonPath("$.headers.World").isEqualTo("Hello");

	}

	@Test
	public void toStringFormat() {
		Config config = new Config();
		config.setInClass(String.class);
		AsyncPredicate<ServerWebExchange> predicate = new ReadBodyRoutePredicateFactory().applyAsync(config);
		assertThat(predicate.toString()).contains("ReadBody: " + config.getInClass());
	}

	@EnableAutoConfiguration
	@SpringBootConfiguration
	@LoadBalancerClients({ @LoadBalancerClient(name = "message", configuration = TestLoadBalancerConfig.class),
			@LoadBalancerClient(name = "messageChannel", configuration = TestLoadBalancerConfig.class) })
	@Import(PermitAllSecurityConfiguration.class)
	@RestController
	public static class TestConfig {

		@Bean
		public RouteLocator routeLocator(RouteLocatorBuilder builder) {
			return builder.routes()
					.route(p -> p.path("/events").and().method(HttpMethod.POST).and()
							.readBody(Event.class, eventPredicate("message.channels"))
							.filters(f -> f.setPath("/messageChannel/events")).uri("lb://messageChannel"))
					.route(p -> p.path("/events").and().method(HttpMethod.POST).and()
							.readBody(Event.class, eventPredicate("message")).filters(f -> f.setPath("/message/events"))
							.uri("lb://message"))
					.build();
		}

		private Predicate<Event> eventPredicate(String type) {
			return r -> r.getFoo().equals(type);
		}

		@PostMapping(path = "message/events", produces = MediaType.APPLICATION_JSON_VALUE)
		public String messageEvents(@RequestBody Event e) {
			return "{\"headers\":{\"Hello\":\"World\"}}";
		}

		@PostMapping(path = "messageChannel/events", produces = MediaType.APPLICATION_JSON_VALUE)
		public String messageChannelEvents(@RequestBody Event e) {
			return "{\"headers\":{\"World\":\"Hello\"}}";
		}

	}

	static class Event {

		private String foo;

		private String bar;

		Event() {
		}

		Event(String foo, String bar) {
			this.foo = foo;
			this.bar = bar;
		}

		public String getFoo() {
			return foo;
		}

		public void setFoo(String foo) {
			this.foo = foo;
		}

		public String getBar() {
			return bar;
		}

		public void setBar(String bar) {
			this.bar = bar;
		}

	}

}
