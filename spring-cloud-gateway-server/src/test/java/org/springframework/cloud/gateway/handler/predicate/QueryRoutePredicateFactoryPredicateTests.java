/*
 * Copyright 2013-2024 the original author or authors.
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
import org.junit.jupiter.api.extension.ExtendWith;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.cloud.gateway.handler.predicate.QueryRoutePredicateFactory.Config;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.cloud.gateway.support.HasConfig;
import org.springframework.cloud.gateway.test.BaseWebClientTests;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.web.server.ServerWebExchange;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

/**
 * Test class for {@link QueryRoutePredicateFactory} for <code>predicate</code> parameter.
 *
 * @see QueryRoutePredicateFactory
 */
@SpringBootTest(webEnvironment = RANDOM_PORT)
@DirtiesContext
@ExtendWith(OutputCaptureExtension.class)
public class QueryRoutePredicateFactoryPredicateTests extends BaseWebClientTests {

	@Test
	public void noQueryParamWorks(CapturedOutput output) {
		this.testClient.get()
			.uri("/get")
			.exchange()
			.expectStatus()
			.isOk()
			.expectHeader()
			.valueEquals(ROUTE_ID_HEADER, "default_path_to_httpbin");
		assertThat(output).doesNotContain("Error applying predicate for route: foo_query_param");
	}

	@Test
	public void queryParamPredicateTrue() {
		this.testClient.get()
			.uri("/get?foo=1234567")
			.exchange()
			.expectStatus()
			.isOk()
			.expectHeader()
			.valueEquals(ROUTE_ID_HEADER, "foo_query_param");
	}

	@Test
	public void queryParamPredicateFalse(CapturedOutput output) {
		this.testClient.get()
			.uri("/get?foo=123")
			.exchange()
			.expectStatus()
			.isOk()
			.expectHeader()
			.valueEquals(ROUTE_ID_HEADER, "default_path_to_httpbin");
		assertThat(output).doesNotContain("Error applying predicate for route: foo_query_param");
	}

	@Test
	public void emptyQueryParamWorks(CapturedOutput output) {
		this.testClient.get()
			.uri("/get?foo")
			.exchange()
			.expectStatus()
			.isOk()
			.expectHeader()
			.valueEquals(ROUTE_ID_HEADER, "default_path_to_httpbin");
		assertThat(output).doesNotContain("Error applying predicate for route: foo_query_param");
	}

	@Test
	public void testConfig() {
		Config config = new Config();
		config.setParam("query_param");
		Predicate<ServerWebExchange> predicate = new QueryRoutePredicateFactory().apply(config);
		assertThat(predicate).isInstanceOf(HasConfig.class);
		assertThat(config).isSameAs(((HasConfig) predicate).getConfig());
	}

	@Test
	public void toStringFormat() {
		Config config = new Config();
		config.setParam("query_param");
		Predicate<ServerWebExchange> predicate = new QueryRoutePredicateFactory().apply(config);
		assertThat(predicate.toString()).contains("Query: param=query_param");
	}

	@EnableAutoConfiguration
	@SpringBootConfiguration
	@Import(DefaultTestConfig.class)
	public static class TestConfig {

		private static final int PARAM_LENGTH = 5;

		@Value("${test.uri}")
		private String uri;

		@Bean
		RouteLocator queryRouteLocator(RouteLocatorBuilder builder) {
			return builder.routes()
				.route("foo_query_param",
						r -> r.query("foo", queryParamPredicate()).filters(f -> f.prefixPath("/httpbin")).uri(this.uri))
				.build();
		}

		private Predicate<String> queryParamPredicate() {
			return p -> p == null ? false : p.length() > PARAM_LENGTH;
		}

	}

}
