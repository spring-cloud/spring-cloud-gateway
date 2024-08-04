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

package org.springframework.cloud.gateway.filter.factory;

import java.net.URI;

import org.junit.jupiter.api.Test;

import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.RedirectToGatewayFilterFactory.Config;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.cloud.gateway.test.BaseWebClientTests;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.test.annotation.DirtiesContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

@SpringBootTest(webEnvironment = RANDOM_PORT)
@DirtiesContext
public class RedirectToGatewayFilterFactoryTests extends BaseWebClientTests {

	@Test
	public void redirectToFilterWorks() {
		testClient.get()
			.uri("/")
			.header("Host", "www.redirectto.org")
			.exchange()
			.expectStatus()
			.isEqualTo(HttpStatus.FOUND)
			.expectHeader()
			.valueEquals(HttpHeaders.LOCATION, "https://example.org");
	}

	@Test
	public void redirectToUrlDoesNotPassQueryParametersByDefault() {
		testClient.get()
			.uri("/?membership=gold")
			.header("Host", "www.redirectto.org")
			.exchange()
			.expectStatus()
			.isEqualTo(HttpStatus.FOUND)
			.expectHeader()
			.valueEquals(HttpHeaders.LOCATION, "https://example.org");
	}

	@Test
	public void redirectToUrlAddsQueryParametersWhenEnabledOnFilter() {
		testClient.get()
			.uri("/?membership=gold")
			.header("Host", "queryparams.redirectto.org")
			.exchange()
			.expectStatus()
			.isEqualTo(HttpStatus.FOUND)
			.expectHeader()
			.valueEquals(HttpHeaders.LOCATION, "https://example.org?membership=gold");
	}

	@Test
	public void redirectToRelativeUrlFilterWorks() {
		testClient.get()
			.uri("/")
			.header("Host", "www.relativeredirect.org")
			.exchange()
			.expectStatus()
			.isEqualTo(HttpStatus.FOUND)
			.expectHeader()
			.valueEquals(HttpHeaders.LOCATION, "/index.html#/customers");
	}

	@Test
	public void redirectToRelativeUrlFilterWorksWithStrStatusCode() {
		testClient.get()
			.uri("/")
			.header("Host", "strcode.relativeredirect.org")
			.exchange()
			.expectStatus()
			.isEqualTo(HttpStatus.FOUND)
			.expectHeader()
			.valueEquals(HttpHeaders.LOCATION, "/index.html#/customers");
	}

	@Test
	public void redirectToRelativeUrlDoesNotPassQueryParametersByDefault() {
		testClient.get()
			.uri("/?membership=gold")
			.header("Host", "www.relativeredirect.org")
			.exchange()
			.expectStatus()
			.isEqualTo(HttpStatus.FOUND)
			.expectHeader()
			.valueEquals(HttpHeaders.LOCATION, "/index.html#/customers");
	}

	@Test
	public void redirectToRelativeUrlAddsQueryParametersWhenEnabledOnFilter() {
		testClient.get()
			.uri("/?membership=gold")
			.header("Host", "queryparams.relativeredirect.org")
			.exchange()
			.expectStatus()
			.isEqualTo(HttpStatus.FOUND)
			.expectHeader()
			.valueEquals(HttpHeaders.LOCATION, "/index.html?membership=gold#/customers");
	}

	@Test
	public void toStringFormat() {
		Config config = new Config();
		config.setStatus("301");
		config.setUrl("http://newurl");
		config.setIncludeRequestParams(true);
		GatewayFilter filter = new RedirectToGatewayFilterFactory().apply(config);
		assertThat(filter.toString()).contains("301").contains("http://newurl").contains("true");
	}

	@EnableAutoConfiguration
	@SpringBootConfiguration
	@Import(DefaultTestConfig.class)
	public static class TestConfig {

		@Bean
		public RouteLocator testRouteLocator(RouteLocatorBuilder builder) {
			return builder.routes()
				.route("relative_redirect_uri_object",
						r -> r.host("strcode.relativeredirect.org")
							.filters(f -> f.redirect("302", URI.create("/index.html#/customers")))
							.uri("no://op"))
				.route("relative_redirect_with_query_params",
						r -> r.host("queryparams.relativeredirect.org")
							.filters(f -> f.redirect(302, "/index.html#/customers", true))
							.uri("no://op"))
				.route("relative_redirect",
						r -> r.host("**.relativeredirect.org")
							.filters(f -> f.redirect(302, "/index.html#/customers"))
							.uri("no://op"))
				.build();
		}

	}

}
