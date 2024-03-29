/*
 * Copyright 2013-2023 the original author or authors.
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


import java.util.Map;

import org.junit.jupiter.api.Test;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.util.LinkedMultiValueMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.cloud.gateway.server.mvc.test.TestUtils.getMap;


@SpringBootTest(properties = {"spring.cloud.gateway.mvc.http-client.type=jdk",
		"spring.cloud.gateway.mvc.remove-content-length-request-headers-filter.enabled=false"},
		webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class DisableRemoveContentLengthFilterTests extends ServerMvcIntegrationTests {

	@Test
	void formUrlencodedWorks() {
		LinkedMultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
		formData.add("baz", "bam");

		// @formatter:off
		restClient.post().uri("/post?foo=fooquery").header("test", "formurlencoded")
				.contentType(FORM_URL_ENCODED_CONTENT_TYPE)
				.bodyValue(formData)
				.exchange()
				.expectStatus().isOk()
				.expectBody(Map.class).consumeWith(result -> {
					Map map = result.getResponseBody();
					Map<String, Object> form = getMap(map, "form");
					assertThat(form).containsEntry("baz", "bam");
				});
	}
}
