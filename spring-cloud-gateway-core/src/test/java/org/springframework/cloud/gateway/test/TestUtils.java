/*
 * Copyright 2013-2019 the original author or authors.
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

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.ClientResponse;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Spencer Gibb
 */
public class TestUtils {

	@SuppressWarnings("unchecked")
	public static Map<String, Object> getMap(Map response, String key) {
		assertThat(response).containsKey(key).isInstanceOf(Map.class);
		return (Map<String, Object>) response.get(key);
	}

	public static List<String> getMultiValuedHeader(Set<Map<String, List<String>>> response, String key) {
		Optional<Map<String, List<String>>> findFirst = response.stream().filter(i -> i.containsKey(key)).findFirst();
		if(!findFirst.isPresent()) {
			return Collections.emptyList();
		} else {
			return findFirst.get().get(key);
		}
	}

	public static void assertStatus(ClientResponse response, HttpStatus status) {
		HttpStatus statusCode = response.statusCode();
		assertThat(statusCode).isEqualTo(status);
	}

}
