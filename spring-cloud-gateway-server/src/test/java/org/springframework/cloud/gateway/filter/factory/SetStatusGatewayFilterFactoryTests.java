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

import org.hamcrest.Matchers;
import org.junit.Test;
import org.junit.runner.RunWith;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.SetStatusGatewayFilterFactory.Config;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.cloud.gateway.test.BaseWebClientTests;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.reactive.server.WebTestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = RANDOM_PORT)
@DirtiesContext
public class SetStatusGatewayFilterFactoryTests extends BaseWebClientTests {

	@Autowired
	private SetStatusGatewayFilterFactory filterFactory;

	@Test
	public void setStatusIntWorks() {
		setStatusStringTest("www.setstatusint.org", HttpStatus.UNAUTHORIZED);
	}

	@Test
	public void setStatusStringWorks() {
		setStatusStringTest("www.setstatusstring.org", HttpStatus.BAD_REQUEST);
	}

	@Test
	public void nonStandardCodeWorks() {
		HttpHeaders headers = new HttpHeaders();
		headers.set(HttpHeaders.HOST, "www.setcustomstatus.org");
		ResponseEntity<String> response = new TestRestTemplate().exchange(baseUri + "/headers", HttpMethod.GET,
				new HttpEntity<>(headers), String.class);
		assertThat(response.getStatusCodeValue()).isEqualTo(432);

		// https://jira.spring.io/browse/SPR-16748
		/*
		 * testClient.get() .uri("/status/432") .exchange() .expectStatus().isEqualTo(432)
		 * .expectBody(String.class).isEqualTo("Failed with 432");
		 */
	}

	@Test
	public void shouldSetStatusIntAndAddOriginalHeader() {
		String headerName = "original-http-status";
		filterFactory.setOriginalStatusHeaderName(headerName);
		setStatusStringTest("www.setstatusint.org", HttpStatus.UNAUTHORIZED).expectHeader().value(headerName,
				Matchers.is("[200]"));

	}

	@Test
	public void toStringFormat() {
		Config config = new Config();
		config.setStatus("401");
		GatewayFilter filter = new SetStatusGatewayFilterFactory().apply(config);
		assertThat(filter.toString()).contains("401");
	}

	private WebTestClient.ResponseSpec setStatusStringTest(String host, HttpStatus status) {
		return testClient.get().uri("/headers").header("Host", host).exchange().expectStatus().isEqualTo(status);
	}

	@EnableAutoConfiguration
	@SpringBootConfiguration
	@Import(DefaultTestConfig.class)
	public static class TestEnumConfig {

		@Value("${test.uri}")
		String uri;

		@Bean
		public RouteLocator enumRouteLocator(RouteLocatorBuilder builder) {
			return builder.routes().route("test_enum_http_status",
					r -> r.host("*.setenumstatus.org").filters(f -> f.setStatus(HttpStatus.UNAUTHORIZED)).uri(uri))
					.build();
		}

	}

	@EnableAutoConfiguration
	@SpringBootConfiguration
	@Import(DefaultTestConfig.class)
	public static class TestConfig {

		@Value("${test.uri}")
		String uri;

		@Bean
		public RouteLocator myRouteLocator(RouteLocatorBuilder builder) {
			// @formatter:off
			return builder.routes()
					.route("test_custom_http_status",
							r -> r.host("*.setcustomstatus.org")
									.filters(f -> f.setStatus(432))
									.uri(uri))
					.build();
			// @formatter:on
		}

	}

}
