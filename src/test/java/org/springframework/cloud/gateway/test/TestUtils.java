package org.springframework.cloud.gateway.test;

import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.ClientResponse;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Spencer Gibb
 */
public class TestUtils {
	public static Map<String, Object> getMap(Map response, String key) {
		assertThat(response).containsKey(key).isInstanceOf(Map.class);
		return (Map<String, Object>) response.get(key);
	}

	public static void assertStatus(ClientResponse response, HttpStatus status) {
		HttpStatus statusCode = response.statusCode();
		assertThat(statusCode).isEqualTo(status);
	}
}
