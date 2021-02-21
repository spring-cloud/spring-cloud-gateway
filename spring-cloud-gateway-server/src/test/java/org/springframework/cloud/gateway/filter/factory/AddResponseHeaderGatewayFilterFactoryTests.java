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

import org.junit.Test;
import org.junit.runner.RunWith;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractNameValueGatewayFilterFactory.NameValueConfig;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.cloud.gateway.test.BaseWebClientTests;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.web.util.UriComponentsBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = RANDOM_PORT)
@DirtiesContext
public class AddResponseHeaderGatewayFilterFactoryTests extends BaseWebClientTests {

	@Test
	public void testResposneHeaderFilter() {
		URI uri = UriComponentsBuilder.fromUriString(this.baseUri + "/headers").build(true).toUri();
		String host = "www.addresponseheader.org";
		String expectedValue = "Bar";
		testClient.get().uri(uri).header("Host", host).exchange().expectHeader().valueEquals("X-Request-Foo",
				expectedValue);
	}

	@Test
	public void testResposneHeaderFilterJavaDsl() {
		URI uri = UriComponentsBuilder.fromUriString(this.baseUri + "/get").build(true).toUri();
		String host = "www.addresponseheaderjava.org";
		String expectedValue = "myresponsevalue-www";
		testClient.get().uri(uri).header("Host", host).exchange().expectHeader().valueEquals("example", expectedValue);
	}

	@Test
	public void toStringFormat() {
		NameValueConfig config = new NameValueConfig().setName("myname").setValue("myvalue");
		GatewayFilter filter = new AddResponseHeaderGatewayFilterFactory().apply(config);
		assertThat(filter.toString()).contains("myname").contains("myvalue");
	}

	@EnableAutoConfiguration
	@SpringBootConfiguration
	@Import(DefaultTestConfig.class)
	public static class TestConfig {

		@Value("${test.uri}")
		String uri;

		@Bean
		public RouteLocator testRouteLocator(RouteLocatorBuilder builder) {
			return builder.routes()
					.route("add_response_header_java_test",
							r -> r.path("/get").and().host("{sub}.addresponseheaderjava.org").filters(
									f -> f.prefixPath("/httpbin").addResponseHeader("example", "myresponsevalue-{sub}"))
									.uri(uri))
					.build();
		}

	}

}
