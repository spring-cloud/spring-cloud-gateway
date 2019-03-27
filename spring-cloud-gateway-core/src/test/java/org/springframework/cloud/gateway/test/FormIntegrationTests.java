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

package org.springframework.cloud.gateway.test;

import java.nio.charset.Charset;
import java.util.Map;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;
import static org.springframework.cloud.gateway.test.TestUtils.getMap;
import static org.springframework.web.reactive.function.BodyExtractors.toMono;

import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = RANDOM_PORT)
@DirtiesContext
@SuppressWarnings("unchecked")
public class FormIntegrationTests extends BaseWebClientTests {

	public static final MediaType CONTENT_TYPE = new MediaType(MediaType.APPLICATION_FORM_URLENCODED, Charset.forName("UTF-8"));

	@Test
	public void formUrlencodedWorks() {
		LinkedMultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
		formData.add("foo", "bar");
		formData.add("baz", "bam");

		testClient.post()
				.uri("/post")
				.contentType(CONTENT_TYPE)
				.body(BodyInserters.fromFormData(formData))
				.exchange()
				.expectStatus().isOk()
				.expectBody(Map.class)
				.consumeWith(result -> {
					Map map = result.getResponseBody();
					Map<String, Object> form = getMap(map, "form");
					assertThat(form).containsEntry("foo", "bar");
					assertThat(form).containsEntry("baz", "bam");
				});
	}

	@Test
	public void multipartFormDataWorks() {
		ClassPathResource img = new ClassPathResource("1x1.png");

		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.IMAGE_PNG);

		HttpEntity<ClassPathResource> entity = new HttpEntity<>(img, headers);

		MultiValueMap<String, Object> parts = new LinkedMultiValueMap<>();
		parts.add("imgpart", entity);

		Mono<Map> result = webClient.post()
				.uri("/post")
				.contentType(MediaType.MULTIPART_FORM_DATA)
				.body(BodyInserters.fromMultipartData(parts))
				.exchange()
				.flatMap(response -> response.body(toMono(Map.class)));

		StepVerifier.create(result)
				.consumeNextWith(map -> {
					Map<String, Object> files = getMap(map, "files");
					assertThat(files).containsKey("imgpart");
					String file = (String) files.get("imgpart");
					assertThat(file).startsWith("data:").contains(";base64,");
				})
				.expectComplete()
				.verify(DURATION);
	}

	@EnableAutoConfiguration
	@SpringBootConfiguration
	@Import(DefaultTestConfig.class)
	public static class TestConfig { }

}
