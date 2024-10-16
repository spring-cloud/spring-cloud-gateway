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

package org.springframework.cloud.gateway.server.mvc.filter;

import java.util.Collections;

import org.junit.jupiter.api.Test;

import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.web.servlet.function.ServerRequest;

import static org.assertj.core.api.Assertions.assertThat;

public class TransferEncodingNormalizationRequestHeadersFilterTests {

	@Test
	public void noTransferEncodingWithContentLength() {
		MockHttpServletRequest servletRequest = MockMvcRequestBuilders.post("http://localhost/post")
			.header(HttpHeaders.CONTENT_LENGTH, "6")
			.buildRequest(null);

		HttpHeaders headers = testFilter(ServerRequest.create(servletRequest, Collections.emptyList()));
		assertThat(headers).containsKey(HttpHeaders.CONTENT_LENGTH).doesNotContainKey(HttpHeaders.TRANSFER_ENCODING);
	}

	@Test
	public void transferEncodingWithContentLength() {
		MockHttpServletRequest servletRequest = MockMvcRequestBuilders.post("http://localhost/post")
			.header(HttpHeaders.CONTENT_LENGTH, "6")
			.header(HttpHeaders.TRANSFER_ENCODING, "chunked")
			.buildRequest(null);

		HttpHeaders headers = testFilter(ServerRequest.create(servletRequest, Collections.emptyList()));
		assertThat(headers).doesNotContainKey(HttpHeaders.CONTENT_LENGTH).containsKey(HttpHeaders.TRANSFER_ENCODING);
	}

	@Test
	public void transferEncodingCaseInsensitiveWithContentLength() {
		MockHttpServletRequest servletRequest = MockMvcRequestBuilders.post("http://localhost/post")
			.header(HttpHeaders.CONTENT_LENGTH, "6")
			.header(HttpHeaders.TRANSFER_ENCODING, "Chunked ")
			.buildRequest(null);

		HttpHeaders headers = testFilter(ServerRequest.create(servletRequest, Collections.emptyList()));
		assertThat(headers).doesNotContainKey(HttpHeaders.CONTENT_LENGTH).containsKey(HttpHeaders.TRANSFER_ENCODING);
	}

	private HttpHeaders testFilter(ServerRequest request) {
		TransferEncodingNormalizationRequestHeadersFilter filter = new TransferEncodingNormalizationRequestHeadersFilter();
		return filter.apply(request.headers().asHttpHeaders(), request);
	}

}
