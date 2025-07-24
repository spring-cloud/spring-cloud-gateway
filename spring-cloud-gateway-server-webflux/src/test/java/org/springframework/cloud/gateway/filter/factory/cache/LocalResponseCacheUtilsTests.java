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

package org.springframework.cloud.gateway.filter.factory.cache;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import org.springframework.mock.http.server.reactive.MockServerHttpRequest;

import static org.assertj.core.api.Assertions.assertThat;

class LocalResponseCacheUtilsTests {

	@ParameterizedTest
	@ValueSource(strings = { "", "no-store", "no-store, wrong-no-cache", "s-no-cache" })
	void shouldNotIdentifyRequestAsNoCacheRequest(String cacheControl) {
		MockServerHttpRequest httpRequest = MockServerHttpRequest.get("https://this")
			.header("Cache-Control", cacheControl)
			.build();

		boolean result = LocalResponseCacheUtils.isNoCacheRequest(httpRequest);

		assertThat(result).isFalse();
	}

	@ParameterizedTest
	@ValueSource(strings = { "no-cache", "s-no-cache, no-cache", "private,no-cache", " no-cache", "no-cache " })
	void shouldIdentifyRequestAsNoCacheRequest(String cacheControl) {
		MockServerHttpRequest httpRequest = MockServerHttpRequest.get("https://this")
			.header("Cache-Control", cacheControl)
			.build();

		boolean result = LocalResponseCacheUtils.isNoCacheRequest(httpRequest);

		assertThat(result).isTrue();
	}

}
