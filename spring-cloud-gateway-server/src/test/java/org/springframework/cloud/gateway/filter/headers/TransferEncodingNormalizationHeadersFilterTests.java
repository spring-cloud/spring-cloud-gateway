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

package org.springframework.cloud.gateway.filter.headers;

import org.junit.jupiter.api.Test;

import org.springframework.http.HttpHeaders;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Spencer Gibb
 */
public class TransferEncodingNormalizationHeadersFilterTests {

	@Test
	public void noTransferEncodingWithContentLength() {
		MockServerHttpRequest.BaseBuilder<?> builder = MockServerHttpRequest.post("http://localhost/post")
				.header(HttpHeaders.CONTENT_LENGTH, "6");

		HttpHeaders headers = testFilter(MockServerWebExchange.from(builder));
		assertThat(headers).containsKey(HttpHeaders.CONTENT_LENGTH).doesNotContainKey(HttpHeaders.TRANSFER_ENCODING);
	}

	@Test
	public void transferEncodingWithContentLength() {
		MockServerHttpRequest.BaseBuilder<?> builder = MockServerHttpRequest.post("http://localhost/post")
				.header(HttpHeaders.CONTENT_LENGTH, "6").header(HttpHeaders.TRANSFER_ENCODING, "chunked");

		HttpHeaders headers = testFilter(MockServerWebExchange.from(builder));
		assertThat(headers).doesNotContainKey(HttpHeaders.CONTENT_LENGTH).containsKey(HttpHeaders.TRANSFER_ENCODING);
	}

	@Test
	public void transferEncodingCaseInsensitiveWithContentLength() {
		MockServerHttpRequest.BaseBuilder<?> builder = MockServerHttpRequest.post("http://localhost/post")
				.header(HttpHeaders.CONTENT_LENGTH, "6").header(HttpHeaders.TRANSFER_ENCODING, "Chunked ");

		HttpHeaders headers = testFilter(MockServerWebExchange.from(builder));
		assertThat(headers).doesNotContainKey(HttpHeaders.CONTENT_LENGTH).containsKey(HttpHeaders.TRANSFER_ENCODING);
	}

	private HttpHeaders testFilter(MockServerWebExchange exchange) {
		TransferEncodingNormalizationHeadersFilter filter = new TransferEncodingNormalizationHeadersFilter();
		return filter.filter(exchange.getRequest().getHeaders(), exchange);
	}

}
