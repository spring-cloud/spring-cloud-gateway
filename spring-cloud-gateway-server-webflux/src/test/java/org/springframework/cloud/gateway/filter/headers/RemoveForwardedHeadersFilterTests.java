/*
 * Copyright 2013-present the original author or authors.
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

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;

import org.junit.jupiter.api.Test;

import org.springframework.http.HttpHeaders;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.cloud.gateway.filter.headers.ForwardedHeadersFilter.FORWARDED_HEADER;

/**
 * @author Spencer Gibb
 */
public class RemoveForwardedHeadersFilterTests {

	@Test
	public void forwardedHeaderDoesNotExist() throws UnknownHostException {
		MockServerHttpRequest request = MockServerHttpRequest.get("http://localhost/get")
			.remoteAddress(new InetSocketAddress(InetAddress.getByName("10.0.0.1"), 80))
			.header(HttpHeaders.HOST, "myhost")
			.build();

		RemoveForwardedHeadersFilter filter = new RemoveForwardedHeadersFilter();

		HttpHeaders headers = filter.filter(request.getHeaders(), MockServerWebExchange.from(request));

		assertThat(headers.headerNames()).doesNotContain(FORWARDED_HEADER);
	}

	@Test
	public void forwardedHeaderExists() throws UnknownHostException {
		MockServerHttpRequest request = MockServerHttpRequest.get("http://localhost/get")
			.remoteAddress(new InetSocketAddress(InetAddress.getByName("10.0.0.1"), 80))
			.header(FORWARDED_HEADER, "for=12.34.56.78;host=example.com;proto=https, for=23.45.67.89")
			.build();

		RemoveForwardedHeadersFilter filter = new RemoveForwardedHeadersFilter();

		HttpHeaders headers = filter.filter(request.getHeaders(), MockServerWebExchange.from(request));

		assertThat(headers.headerNames()).doesNotContain(FORWARDED_HEADER);
	}

}
