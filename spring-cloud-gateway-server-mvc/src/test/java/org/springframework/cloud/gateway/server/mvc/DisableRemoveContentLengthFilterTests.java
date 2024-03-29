package org.springframework.cloud.gateway.server.mvc;


import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.springframework.cloud.gateway.server.mvc.test.TestUtils.getMap;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.util.LinkedMultiValueMap;

import static org.assertj.core.api.Assertions.assertThat;


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
					assertThat(form).containsEntry("foo", "bar");
					assertThat(form).containsEntry("baz", "bam");
				});
	}
}
