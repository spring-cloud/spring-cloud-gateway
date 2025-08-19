/*
 * Copyright 2013-2025 the original author or authors.
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

package org.springframework.cloud.gateway.filter.headers;

import java.util.Map;

import org.junit.jupiter.api.Test;

import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.test.BaseWebClientTests;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.web.bind.annotation.RestController;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;
import static org.springframework.cloud.gateway.filter.headers.ForwardedHeadersFilter.FORWARDED_HEADER;
import static org.springframework.cloud.gateway.filter.headers.XForwardedHeadersFilter.X_FORWARDED_FOR_HEADER;
import static org.springframework.cloud.gateway.filter.headers.XForwardedHeadersFilter.X_FORWARDED_HOST_HEADER;
import static org.springframework.cloud.gateway.filter.headers.XForwardedHeadersFilter.X_FORWARDED_PORT_HEADER;
import static org.springframework.cloud.gateway.filter.headers.XForwardedHeadersFilter.X_FORWARDED_PROTO_HEADER;
import static org.springframework.cloud.gateway.test.TestUtils.getMap;

/**
 * Integration tests making sure all forwarded and x-forwarded headers are removed if no
 * trusted proxies set.
 *
 * @author Spencer Gibb
 */
@SpringBootTest(webEnvironment = RANDOM_PORT,
		properties = { "spring.cloud.gateway.server.webflux.forwarded.enabled=true", "spring.cloud.gateway.server.webflux.x-forwarded.enabled=true",
				"logging.level.org.springframework.cloud.gateway.filter.headers=TRACE" })
@DirtiesContext
public class RemoveForwardedHeadersFilterIntegrationTests extends BaseWebClientTests {

	@Test
	void forwardedHeadersRemoved() {
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
				assertThat(headers).doesNotContainKeys(FORWARDED_HEADER,
						X_FORWARDED_FOR_HEADER, X_FORWARDED_HOST_HEADER,
						X_FORWARDED_PORT_HEADER, X_FORWARDED_PROTO_HEADER);
			});
	}

	@EnableAutoConfiguration
	@SpringBootConfiguration
	@Import(DefaultTestConfig.class)
	@RestController
	static class TestConfig {

	}

}
