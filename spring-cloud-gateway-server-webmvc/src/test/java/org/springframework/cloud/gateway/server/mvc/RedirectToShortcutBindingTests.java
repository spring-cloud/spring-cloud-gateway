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

package org.springframework.cloud.gateway.server.mvc;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.server.mvc.test.PermitAllSecurityConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = RedirectToShortcutBindingTests.TestApplication.class,
		webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
		properties = { "spring.cloud.gateway.server.webmvc.routes[0].id=redirect-shortcut",
				"spring.cloud.gateway.server.webmvc.routes[0].uri=no://op",
				"spring.cloud.gateway.server.webmvc.routes[0].predicates[0]=Path=/redirect/**",
				"spring.cloud.gateway.server.webmvc.routes[0].filters[0]=RedirectTo=302,https://example.org/,true,true" })
class RedirectToShortcutBindingTests {

	@Autowired
	private TestRestTemplate restTemplate;

	@Test
	void redirectToBindsAllShortcutArguments() {
		ResponseEntity<Void> response = restTemplate.getForEntity("/redirect/test?foo=bar", Void.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FOUND);
		assertThat(response.getHeaders().getFirst(HttpHeaders.LOCATION))
			.isEqualTo("https://example.org/redirect/test?foo=bar");
	}

	@SpringBootConfiguration
	@EnableAutoConfiguration
	@Import(PermitAllSecurityConfiguration.class)
	static class TestApplication {

	}

}
