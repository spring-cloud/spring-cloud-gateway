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

package org.springframework.cloud.gateway.test;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.junit.Test;
import org.junit.runner.RunWith;

import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;
import static org.springframework.cloud.gateway.test.TestUtils.getMap;
import static org.springframework.http.MediaType.APPLICATION_FORM_URLENCODED;
import static org.springframework.http.MediaType.MULTIPART_FORM_DATA;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = RANDOM_PORT)
@DirtiesContext
@SuppressWarnings("unchecked")
public class FormIntegrationTests extends BaseWebClientTests {

	public static final MediaType FORM_URL_ENCODED_CONTENT_TYPE = new MediaType(APPLICATION_FORM_URLENCODED,
			StandardCharsets.UTF_8);

	@Test
	public void formUrlencodedWorks() {
		LinkedMultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
		formData.add("foo", "bar");
		formData.add("baz", "bam");

		// @formatter:off
		testClient.post().uri("/post").contentType(FORM_URL_ENCODED_CONTENT_TYPE)
				.body(BodyInserters.fromFormData(formData))
				.exchange()
				.expectStatus().isOk()
				.expectBody(Map.class).consumeWith(result -> {
					Map map = result.getResponseBody();
					Map<String, Object> form = getMap(map, "form");
					assertThat(form).containsEntry("foo", "bar");
					assertThat(form).containsEntry("baz", "bam");
				});
		// @formatter:on
	}

	@Test
	public void multipartFormDataWorksWebClient() {
		MultiValueMap<String, HttpEntity<?>> formData = createMultipartData();

		// @formatter:off
		testClient.post().uri("/post").contentType(MULTIPART_FORM_DATA)
				.bodyValue(formData)
				.exchange()
				.expectStatus().isOk()
				.expectBody(Map.class)
				.consumeWith(result -> assertMultipartData(result.getResponseBody()));
		// @formatter:on
	}

	@Test
	public void multipartFormDataWorksRestTemplate() {
		MultiValueMap<String, HttpEntity<?>> formData = createMultipartData();
		TestRestTemplate rest = new TestRestTemplate();

		ResponseEntity<Map> response = rest.postForEntity(baseUri + "/post", formData, Map.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertMultipartData(response.getBody());
	}

	private MultiValueMap<String, HttpEntity<?>> createMultipartData() {
		ClassPathResource part = new ClassPathResource("1x1.png");
		MultipartBodyBuilder builder = new MultipartBodyBuilder();
		builder.part("imgpart", part, MediaType.IMAGE_PNG);
		return builder.build();
	}

	private void assertMultipartData(Map responseBody) {
		Map<String, Object> files = getMap(responseBody, "files");
		assertThat(files).containsKey("imgpart");
		String file = (String) files.get("imgpart");
		assertThat(file).startsWith("data:").contains(";base64,");
	}

	@EnableAutoConfiguration
	@SpringBootConfiguration
	@Import(DefaultTestConfig.class)
	public static class TestConfig {

	}

}
