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

package org.springframework.cloud.gateway.server.mvc.filter;

import java.util.Map;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.server.mvc.test.HttpbinTestcontainers;
import org.springframework.cloud.gateway.server.mvc.test.HttpbinUriResolver;
import org.springframework.cloud.gateway.server.mvc.test.PermitAllSecurityConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;
import static org.springframework.cloud.gateway.server.mvc.filter.AfterFilterFunctions.addResponseHeader;
import static org.springframework.cloud.gateway.server.mvc.filter.ForwardedRequestHeadersFilter.FORWARDED_HEADER;
import static org.springframework.cloud.gateway.server.mvc.filter.XForwardedRequestHeadersFilter.X_FORWARDED_FOR_HEADER;
import static org.springframework.cloud.gateway.server.mvc.filter.XForwardedRequestHeadersFilter.X_FORWARDED_HOST_HEADER;
import static org.springframework.cloud.gateway.server.mvc.filter.XForwardedRequestHeadersFilter.X_FORWARDED_PORT_HEADER;
import static org.springframework.cloud.gateway.server.mvc.filter.XForwardedRequestHeadersFilter.X_FORWARDED_PROTO_HEADER;
import static org.springframework.cloud.gateway.server.mvc.handler.GatewayRouterFunctions.route;
import static org.springframework.cloud.gateway.server.mvc.handler.HandlerFunctions.http;
import static org.springframework.cloud.gateway.server.mvc.test.TestUtils.getMap;

/**
 * Integration tests making sure all forwarded and x-forwarded headers are removed if no
 * trusted proxies set.
 *
 * @author Spencer Gibb
 */
@SpringBootTest(webEnvironment = RANDOM_PORT,
		properties = { "spring.cloud.gateway.server.webmvc.forwarded.enabled=true",
				"spring.cloud.gateway.server.webmvc.x-forwarded.enabled=true",
				"spring.cloud.gateway.server.webmvc.trusted-proxies=10.0.0.1",
				"logging.level.org.springframework.cloud.gateway.mvc.filter=TRACE" })
@ContextConfiguration(initializers = HttpbinTestcontainers.class)
@DirtiesContext
public class RemoveForwardedRequestHeadersFilterIntegrationTests {

	@Autowired
	RestTestClient testClient;

	@Test
	public void untrustedForwardedHeadersRemoved() {
		testClient.get()
			.uri("/headers")
			.header(FORWARDED_HEADER, "for=12.34.56.78;host=example.com;proto=https, for=23.45.67.89")
			.header(X_FORWARDED_FOR_HEADER, "192.168.0.2")
			.header(X_FORWARDED_HOST_HEADER, "example.com")
			.header(X_FORWARDED_PORT_HEADER, "443")
			.header(X_FORWARDED_PROTO_HEADER, "https")
			.exchange()
			.expectStatus()
			.isOk()
			.expectBody(Map.class)
			.consumeWith(result -> {
				Map<String, Object> headers = getMap(result.getResponseBody(), "headers");
				// gateway forwarded headers are added, but all the forwarded headers from
				// the request are removed.
				assertThat(getString(headers, FORWARDED_HEADER)).doesNotContain("12.34.56.78", "23.45.67.89");
				assertThat(getString(headers, X_FORWARDED_FOR_HEADER)).doesNotContain("192.168.0.2");
				assertThat(getString(headers, X_FORWARDED_HOST_HEADER)).doesNotContain("example.com");
				assertThat(getString(headers, X_FORWARDED_PORT_HEADER)).doesNotContain("443");
				assertThat(getString(headers, X_FORWARDED_PROTO_HEADER)).doesNotContain("https");
			});
	}

	private static @Nullable String getString(Map<String, Object> headers, String key) {
		return (String) headers.get(key);
	}

	@EnableAutoConfiguration
	@SpringBootConfiguration
	@Import(PermitAllSecurityConfiguration.class)
	public static class TestConfig {

		@Bean
		public RouterFunction<ServerResponse> weightLowRouterFunction() {
			// @formatter:off
			return route("remove_forwarded")
					.GET("/headers", http())
					.before(new HttpbinUriResolver())
					.after(addResponseHeader("X-Route", "remove_forwarded"))
					.build();
			// @formatter:on
		}

	}

}
