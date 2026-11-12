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

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.cloud.gateway.server.mvc.config.GatewayMvcProperties;
import org.springframework.cloud.gateway.server.mvc.test.PermitAllSecurityConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Tests that the {@code RewriteLocationResponseHeader} filter can be configured from
 * properties with any of its optional parameters omitted.
 *
 * @author Sharang Gupta
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT,
		properties = { GatewayMvcProperties.PREFIX + ".function.enabled=false" })
@ActiveProfiles("rewritelocationresponseheaderproperties")
public class RewriteLocationResponseHeaderPropertiesTests {

	private static final String BACKEND_LOCATION = "https://backend.org:443/v1/some/object/id";

	@Autowired
	RestTestClient restClient;

	@LocalServerPort
	int port;

	@Test
	void shortcutWithTrailingEmptyArgsUsesDefaults() {
		assertLocationRewrittenTo("/anything/rewritelocation/trailingdefaults", requestHost() + "/some/object/id");
	}

	@Test
	void shortcutWithoutArgsUsesDefaults() {
		assertLocationRewrittenTo("/anything/rewritelocation/noargs", requestHost() + "/some/object/id");
	}

	@Test
	void namedArgsMayOmitParameters() {
		assertLocationRewrittenTo("/anything/rewritelocation/namedsubset", "https://example-api.com/some/object/id");
	}

	@Test
	void namedArgsWithBlankHostValueUseRequestHost() {
		assertLocationRewrittenTo("/anything/rewritelocation/blankhost", requestHost() + "/some/object/id");
	}

	@Test
	void shortcutWithAllArgsStillWorks() {
		assertLocationRewrittenTo("/anything/rewritelocation/allargs", "https://example-api.com/v1/some/object/id");
	}

	private String requestHost() {
		return "https://localhost:" + port;
	}

	private void assertLocationRewrittenTo(String gatewayPath, String expectedLocation) {
		restClient.get()
			.uri(gatewayPath)
			.exchange()
			.expectStatus()
			.isOk()
			.expectHeader()
			.valueEquals(HttpHeaders.LOCATION, expectedLocation);
	}

	@SpringBootConfiguration
	@EnableAutoConfiguration
	@Import({ PermitAllSecurityConfiguration.class, LocationBackendController.class })
	static class TestConfiguration {

	}

	@RestController
	static class LocationBackendController {

		@GetMapping("/rewritelocation/backend")
		ResponseEntity<Void> locationFromBackend() {
			return ResponseEntity.ok().header(HttpHeaders.LOCATION, BACKEND_LOCATION).build();
		}

	}

}
