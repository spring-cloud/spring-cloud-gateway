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

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.cloud.gateway.test.BaseWebClientTests;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;
import static org.springframework.cloud.gateway.test.TestUtils.getMap;

/**
 * @author Spencer Gibb
 */
@SpringBootTest(webEnvironment = RANDOM_PORT)
@DirtiesContext
public class PreserveHostHeaderGatewayFilterFactoryTests extends BaseWebClientTests {

	@SuppressWarnings("unchecked")
	@Test
	public void preserveHostHeaderGatewayFilterFactoryWorks() {
		testClient.get()
			.uri("/multivalueheaders")
			.header("Host", "www.preservehostheader.org")
			.exchange()
			.expectStatus()
			.isOk()
			.expectBody(Map.class)
			.consumeWith(result -> {
				Map<String, Object> headers = getMap(result.getResponseBody(), "headers");
				assertThat(headers).containsKey("Host");
				List<String> values = (List<String>) headers.get("Host");
				assertThat(values).containsExactly("myhost.net");
			});
	}

	@Test
	public void toStringFormat() {
		GatewayFilter filter = new PreserveHostHeaderGatewayFilterFactory().apply();
		assertThat(filter.toString()).contains("PreserveHostHeader");
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
				.route("test_preserve_host_header", r -> r.order(-1)
					.host("**.preservehostheader.org")
					.filters(f -> f.prefixPath("/httpbin").preserveHostHeader().setRequestHeader("Host", "myhost.net"))
					.uri(uri))
				.build();
		}

	}

}
