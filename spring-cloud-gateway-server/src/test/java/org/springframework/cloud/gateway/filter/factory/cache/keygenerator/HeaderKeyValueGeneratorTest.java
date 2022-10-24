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

package org.springframework.cloud.gateway.filter.factory.cache.keygenerator;

import java.util.List;

import org.junit.jupiter.api.Test;

import org.springframework.http.HttpHeaders;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

/**
 * @author Ignacio Lozano
 */
class HeaderKeyValueGeneratorTest {

	private static final String HEADER_NAME = "X-Header";

	private static final String SINGLE_HEADER_VALUE = "header-value";

	private static final String VALUE1 = "value-1";

	private static final String VALUE2 = "value-2";

	private static final String SEPARATOR = ",";

	@Test
	void exceptionIsThrown_whenConstructorHeaderIsNull() {
		assertThatExceptionOfType(IllegalArgumentException.class)
				.isThrownBy(() -> new HeaderKeyValueGenerator(null, SEPARATOR));
	}

	@Test
	void keyValuePatternIsGenerated_whenOneSingleValueHeaderIsFound() {
		HttpHeaders headers = new HttpHeaders();
		headers.set(HEADER_NAME, SINGLE_HEADER_VALUE);
		MockServerHttpRequest request = MockServerHttpRequest.get("http://this").headers(headers).build();

		String result = new HeaderKeyValueGenerator(HEADER_NAME, SEPARATOR).apply(request);

		assertThat(result).isEqualTo(HEADER_NAME + "=" + SINGLE_HEADER_VALUE);
	}

	@Test
	void keyValuePatternIsGenerated_whenOneMultipleValueHeaderIsFound() {
		HttpHeaders headers = new HttpHeaders();
		headers.put(HEADER_NAME, List.of(VALUE1, VALUE2));
		MockServerHttpRequest request = MockServerHttpRequest.get("http://this").headers(headers).build();

		String result = new HeaderKeyValueGenerator(HEADER_NAME, SEPARATOR).apply(request);

		assertThat(result).isEqualTo(HEADER_NAME + "=" + VALUE1 + SEPARATOR + VALUE2);
	}

	@Test
	void sotedKeyValuePatternIsGenerated_whenOneMultipleUnsortedValueHeaderIsFound() {
		HttpHeaders headers = new HttpHeaders();
		headers.put(HEADER_NAME, List.of(VALUE2, VALUE1));
		MockServerHttpRequest request = MockServerHttpRequest.get("http://this").headers(headers).build();

		String result = new HeaderKeyValueGenerator(HEADER_NAME, SEPARATOR).apply(request);

		assertThat(result).isEqualTo(HEADER_NAME + "=" + VALUE1 + SEPARATOR + VALUE2);
	}

}
