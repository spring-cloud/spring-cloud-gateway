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
import static org.springframework.cloud.gateway.filter.headers.XForwardedHeadersFilter.X_FORWARDED_FOR_HEADER;
import static org.springframework.cloud.gateway.filter.headers.XForwardedHeadersFilter.X_FORWARDED_HOST_HEADER;
import static org.springframework.cloud.gateway.filter.headers.XForwardedHeadersFilter.X_FORWARDED_PORT_HEADER;
import static org.springframework.cloud.gateway.filter.headers.XForwardedHeadersFilter.X_FORWARDED_PROTO_HEADER;

/**
 * @author Spencer Gibb
 */
public class RemoveXForwardedHeadersFilterTests {

	@Test
	public void xForwardedHeaderDoesNotExist() throws UnknownHostException {
		MockServerHttpRequest request = MockServerHttpRequest.get("http://localhost/get")
			.remoteAddress(new InetSocketAddress(InetAddress.getByName("10.0.0.1"), 80))
			.header(HttpHeaders.HOST, "myhost")
			.build();

		RemoveXForwardedHeadersFilter filter = new RemoveXForwardedHeadersFilter();

		HttpHeaders headers = filter.filter(request.getHeaders(), MockServerWebExchange.from(request));

		assertThat(headers.headerNames()).doesNotContain(X_FORWARDED_FOR_HEADER, X_FORWARDED_HOST_HEADER,
				X_FORWARDED_PORT_HEADER, X_FORWARDED_PROTO_HEADER);
	}

	@Test
	public void xForwardedHeaderExists() throws UnknownHostException {
		MockServerHttpRequest request = MockServerHttpRequest.get("http://localhost/get")
			.remoteAddress(new InetSocketAddress(InetAddress.getByName("10.0.0.1"), 80))
			.header(X_FORWARDED_FOR_HEADER, "192.168.0.2")
			.header(X_FORWARDED_HOST_HEADER, "example.com")
			.header(X_FORWARDED_PORT_HEADER, "443")
			.header(X_FORWARDED_PROTO_HEADER, "https")
			.build();

		RemoveXForwardedHeadersFilter filter = new RemoveXForwardedHeadersFilter();

		HttpHeaders headers = filter.filter(request.getHeaders(), MockServerWebExchange.from(request));

		assertThat(headers.headerNames()).doesNotContain(X_FORWARDED_FOR_HEADER, X_FORWARDED_HOST_HEADER,
				X_FORWARDED_PORT_HEADER, X_FORWARDED_PROTO_HEADER);
	}

}
