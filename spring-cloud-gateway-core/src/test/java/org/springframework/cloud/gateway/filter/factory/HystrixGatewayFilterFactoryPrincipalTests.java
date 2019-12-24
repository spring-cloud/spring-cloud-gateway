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
 */

package org.springframework.cloud.gateway.filter.factory;

import java.security.Principal;
import java.util.Collections;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.junit.Test;
import org.junit.runner.RunWith;
import reactor.core.publisher.Mono;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.cloud.gateway.filter.ratelimit.PrincipalNameKeyResolver;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.cloud.gateway.test.BaseWebClientTests;
import org.springframework.cloud.netflix.ribbon.RibbonClient;
import org.springframework.cloud.netflix.ribbon.RibbonClients;
import org.springframework.context.annotation.Bean;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.core.userdetails.MapReactiveUserDetailsService;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = RANDOM_PORT, properties = "debug=true")
@DirtiesContext
public class HystrixGatewayFilterFactoryPrincipalTests extends BaseWebClientTests {

	@Autowired
	private TestPrincipalGatewayFilterFactory testFilterFactory;

	@Test
	public void hystrixPrincipalNotLost() {
		testClient.get().uri("/hystrixprincipal").headers(httpHeaders -> {
			httpHeaders.setBasicAuth("user", "password");
			httpHeaders.set("Host", "www.hystrixsecurity.org");
		}).exchange().expectStatus().isOk().expectBody().jsonPath("$.principal")
				.isEqualTo("user");
		assertThat(testFilterFactory.resolvedPrincipal).isEqualTo("user");
	}

	@RestController
	@SpringBootConfiguration
	@EnableAutoConfiguration
	@RibbonClients({
			@RibbonClient(name = "testservice", configuration = TestRibbonConfig.class) })
	public static class TestConfig {

		@Value("${test.uri}")
		private String uri;

		@RequestMapping("/httpbin/hystrixprincipal")
		public Mono<Map<String, String>> hystrixPrincipal(Mono<Principal> principal) {
			return principal.map(Principal::getName).defaultIfEmpty("Unknown")
					.map(s -> Collections.singletonMap("principal", s));
		}

		@Bean
		public RouteLocator hystrixRouteLocator(RouteLocatorBuilder builder,
				TestPrincipalGatewayFilterFactory filterFactory) {
			return builder.routes()
					.route("hystrix_security", r -> r.host("**.hystrixsecurity.org")
							.filters(f -> f.prefixPath("/httpbin")
									.hystrix(config -> config.setName("securitycmd"))
									.filter(filterFactory.apply("")))
							.uri(uri))
					.build();
		}

		@Bean
		public TestPrincipalGatewayFilterFactory testPrincipalGatewayFilterFactory() {
			return new TestPrincipalGatewayFilterFactory();
		}

		@Bean
		public RecursiveHttpbinFilter recursiveHttpbinFilter() {
			return new RecursiveHttpbinFilter();
		}

		@Bean
		SecurityWebFilterChain springWebFilterChain(ServerHttpSecurity http) {
			return http.httpBasic().and().authorizeExchange()
					.pathMatchers("/hystrixprincipal").authenticated().anyExchange()
					.permitAll().and().build();
		}

		@Bean
		@SuppressWarnings("deprecation")
		public MapReactiveUserDetailsService reactiveUserDetailsService() {
			UserDetails user = User.withDefaultPasswordEncoder().username("user")
					.password("password").roles("USER").build();
			return new MapReactiveUserDetailsService(user);
		}

	}

	public static class TestPrincipalGatewayFilterFactory
			extends AbstractGatewayFilterFactory<Object> {

		private final Log log = LogFactory
				.getLog(TestPrincipalGatewayFilterFactory.class);

		private KeyResolver keyResolver = new PrincipalNameKeyResolver();

		private String resolvedPrincipal;

		public TestPrincipalGatewayFilterFactory() {
			super(Object.class);
		}

		@Override
		public GatewayFilter apply(Object config) {
			return (exchange, chain) -> keyResolver.resolve(exchange)
					.defaultIfEmpty("Empty Principal").flatMap(name -> {
						resolvedPrincipal = name;
						return chain.filter(exchange);
					});
		}

	}

}
