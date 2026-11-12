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

package org.springframework.cloud.gateway.server.mvc.config;

import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.cloud.gateway.server.mvc.test.HttpbinTestcontainers;
import org.springframework.cloud.gateway.server.mvc.test.PermitAllSecurityConfiguration;
import org.springframework.cloud.gateway.server.mvc.test.TestLoadBalancerConfig;
import org.springframework.cloud.loadbalancer.annotation.LoadBalancerClient;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.client.RestTestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.cloud.gateway.server.mvc.test.TestUtils.getMap;

/**
 * End-to-end coverage for the YAML/properties shortcut path of
 * {@code RemoveJsonAttributesResponseBody} (registration → invoke → live filter), which
 * is the failure mode reported in gh-4240.
 *
 * @author Burak Kalayci
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@ActiveProfiles("removejsonattributesshortcuttests")
@ContextConfiguration(initializers = HttpbinTestcontainers.class)
class RemoveJsonAttributesResponseBodyShortcutTests {

	@Autowired
	RestTestClient restClient;

	@BeforeAll
	static void beforeAll() {
		HttpbinTestcontainers.initializeSystemProperties();
	}

	@Test
	@SuppressWarnings("unchecked")
	void shortcutWithoutFlagBuildsRouteAndStripsRootAttributes() {
		restClient.get().uri("/get").exchange().expectStatus().isOk().expectBody(Map.class).consumeWith(res -> {
			Map<String, Object> body = res.getResponseBody();
			assertThat(body).isNotNull();
			// shortcut resolved (no "Unable to find operation") and filter applied
			assertThat(body).doesNotContainKeys("origin", "url");
			assertThat(body).containsKey("headers");
		});
	}

	@Test
	@SuppressWarnings("unchecked")
	void shortcutWithTrailingTrueBuildsRouteAndStripsNestedAttributes() {
		restClient.post()
			.uri("/post")
			.header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
			.header("Foo", "remove-me")
			.header("Bar", "keep-me")
			.body("{}")
			.exchange()
			.expectStatus()
			.isOk()
			.expectBody(Map.class)
			.consumeWith(res -> {
				Map<String, Object> body = res.getResponseBody();
				assertThat(body).isNotNull();
				Map<String, Object> headers = getMap(body, "headers");
				assertThat(headers).isNotNull();
				// recursive true: nested headers.Foo removed (httpbin preserves this
				// casing)
				assertThat(headers).doesNotContainKey("Foo");
				assertThat(headers).containsEntry("Bar", "keep-me");
			});
	}

	@SpringBootConfiguration
	@EnableAutoConfiguration
	@LoadBalancerClient(name = "httpbin", configuration = TestLoadBalancerConfig.Httpbin.class)
	@Import(PermitAllSecurityConfiguration.class)
	static class Config {

	}

}
