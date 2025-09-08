/*
 * Copyright 2013-present the original author or authors.
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

import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.handler.predicate.VersionRoutePredicateFactory.Config;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.cloud.gateway.test.BaseWebClientTests;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.reactive.accept.ApiVersionResolver;
import org.springframework.web.reactive.config.WebFluxConfigurer;
import org.springframework.web.server.ServerWebExchange;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

@SpringBootTest(webEnvironment = RANDOM_PORT)
@ActiveProfiles("versions")
public class VersionRoutePredicateFactoryIntegrationTests extends BaseWebClientTests {

	@Test
	public void versionHeaderWorks() {
		testClient.mutate()
			.build()
			.get()
			.uri("/anything/version11plus")
			.header("X-API-Version", "1.1.0")
			.exchange()
			.expectStatus()
			.isOk()
			.expectHeader()
			.valueEquals("X-Matched-Version", "1.1+");
		testClient.mutate()
			.build()
			.get()
			.uri("/anything/version11plus")
			.header("X-API-Version", "1.5.0")
			.exchange()
			.expectStatus()
			.isOk()
			.expectHeader()
			.valueEquals("X-Matched-Version", "1.1+");
		testClient.mutate()
			.build()
			.get()
			.uri("/anything/version13")
			.header("X-API-Version", "1.3.0")
			.exchange()
			.expectStatus()
			.isOk()
			.expectHeader()
			.valueEquals("X-Matched-Version", "1.3");
		testClient.mutate()
			.build()
			.get()
			.uri("/anything/version20plus")
			.header("X-API-Version", "2.1.0")
			.exchange()
			.expectStatus()
			.isOk()
			.expectHeader()
			.valueEquals("X-Matched-Version", "2.0+");
	}

	@Test
	public void versionMediaTypeWorks() {
		testClient.mutate()
			.build()
			.get()
			.uri("/anything/version11plus")
			.accept(new MediaType(MediaType.APPLICATION_JSON, Map.of("version", "1.1.0")))
			.exchange()
			.expectStatus()
			.isOk()
			.expectHeader()
			.valueEquals("X-Matched-Version", "1.1+");
	}

	@Test
	public void versionRequestParamWorks() {
		testClient.mutate()
			.build()
			.get()
			.uri("/anything/version11plus?apiVersion=1.1.0")
			.exchange()
			.expectStatus()
			.isOk()
			.expectHeader()
			.valueEquals("X-Matched-Version", "1.1+");
	}

	@Test
	public void customVersionResolverBeanWorks() {
		testClient.mutate()
			.build()
			.get()
			.uri("/anything/version11plus?customApiVersionParam=1.1.0")
			.exchange()
			.expectStatus()
			.isOk()
			.expectHeader()
			.valueEquals("X-Matched-Version", "1.1+");
	}

	@Test
	public void invalidVersionNotFound() {
		testClient.mutate()
			.build()
			.get()
			.uri("/anything/version11plus")
			.header("X-API-Version", "1.0.0")
			.exchange()
			.expectStatus()
			.isNotFound();
	}

	@Test
	public void toStringFormat() {
		Config config = new Config();
		config.setVersion("1.1+");
		Predicate<ServerWebExchange> predicate = new VersionRoutePredicateFactory(
				VersionRoutePredicateFactoryTests.apiVersionStrategy())
			.apply(config);
		assertThat(predicate.toString()).contains("Version: 1.1+");
	}

	@Test
	public void testConfig() {
		try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
			Validator validator = factory.getValidator();

			Config config = new Config();
			config.setVersion("1.1+");

			assertThat(validator.validate(config)).isEmpty();
		}
	}

	@Test
	public void testConfigNullField() {
		try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
			Validator validator = factory.getValidator();

			Config config = new Config();
			Set<ConstraintViolation<Config>> validate = validator.validate(config);

			assertThat(validate).hasSize(1);
		}
	}

	@Test
	public void testConfigBlankField() {
		try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
			Validator validator = factory.getValidator();

			Config config = new Config();
			config.setVersion(" ");
			Set<ConstraintViolation<Config>> validate = validator.validate(config);

			assertThat(validate).hasSize(1);
		}
	}

	@EnableAutoConfiguration
	@SpringBootConfiguration
	@Import(DefaultTestConfig.class)
	// implements WebFluxConfigurer required
	public static class TestConfig implements WebFluxConfigurer {

		@Value("${test.uri}")
		String uri;

		@Bean
		ApiVersionResolver customApiVersionResolver() {
			return exchange -> exchange.getRequest().getQueryParams().getFirst("customApiVersionParam");
		}

		@Bean
		public RouteLocator testRouteLocator(RouteLocatorBuilder builder) {
			return builder.routes()
				.route("version11plus_dsl",
						r -> r.path("/anything/version11plus")
							.and()
							.version("1.1+")
							.filters(f -> f.prefixPath("/httpbin").setResponseHeader("X-Matched-Version", "1.1+"))
							.uri(uri))
				.route("version20plus_dsl",
						r -> r.path("/anything/version20plus")
							.and()
							.version("2.0+")
							.filters(f -> f.prefixPath("/httpbin").setResponseHeader("X-Matched-Version", "2.0+"))
							.uri(uri))
				.build();

		}

	}

}
