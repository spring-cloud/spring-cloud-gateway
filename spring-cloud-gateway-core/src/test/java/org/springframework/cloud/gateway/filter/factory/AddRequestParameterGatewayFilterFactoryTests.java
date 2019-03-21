/*
 * Copyright 2013-2017 the original author or authors.
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

package org.springframework.cloud.gateway.filter.factory;

import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URLDecoder;
import java.util.Map;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.cloud.gateway.test.BaseWebClientTests;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.web.util.UriComponentsBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.containsEncodedParts;
import static org.springframework.cloud.gateway.test.TestUtils.getMap;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = RANDOM_PORT)
@DirtiesContext
@ActiveProfiles(profiles = "request-parameter-web-filter")
public class AddRequestParameterGatewayFilterFactoryTests extends BaseWebClientTests {

	@Test
	public void addRequestParameterFilterWorksBlankQuery() {
		testRequestParameterFilter(null, null);
	}

	@Test
	public void addRequestParameterFilterWorksNonBlankQuery() {
		testRequestParameterFilter("baz", "bam");
	}

	@Test
	public void addRequestParameterFilterWorksEncodedQuery() {
		testRequestParameterFilter("name", "%E6%89%8E%E6%A0%B9");
	}

	@Test
	public void addRequestParameterFilterWorksEncodedQueryJavaDsl() {
		testRequestParameterFilter("www.addreqparamjava.org", "ValueB", "javaname", "%E6%89%8E%E6%A0%B9");
	}

	private void testRequestParameterFilter(String name, String value) {
		testRequestParameterFilter("www.addrequestparameter.org", "ValueA", name, value);
	}

    private void testRequestParameterFilter(String host, String expectedValue, String name, String value) {
		String query;
		if (name != null) {
			query = "?" + name + "=" + value;
		} else {
			query = "";
		}
		URI uri = UriComponentsBuilder.fromUriString(this.baseUri+"/get" + query).build(true).toUri();
		boolean checkForEncodedValue = containsEncodedParts(uri);
		testClient.get()
				.uri(uri)
				.header("Host", host)
				.exchange()
				.expectBody(Map.class)
				.consumeWith(response -> {
					Map<String, Object> args = getMap(response.getResponseBody(), "args");
                    assertThat(args).containsEntry("example", expectedValue);
                    if (name != null) {
                        if (checkForEncodedValue) {
                            try {
                                assertThat(args).containsEntry(name, URLDecoder.decode(value, "UTF-8"));
                            } catch (UnsupportedEncodingException e) {
                                throw new RuntimeException(e);
                            }
                        } else {
                            assertThat(args).containsEntry(name, value);
                        }
                    }
                });
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
					.route("add_request_param_java_test", r ->
							r.path("/get").and().host("**.addreqparamjava.org")
									.filters(f -> f.prefixPath("/httpbin").addRequestParameter("example", "ValueB"))
									.uri(uri))
					.build();
		}
	}

}
