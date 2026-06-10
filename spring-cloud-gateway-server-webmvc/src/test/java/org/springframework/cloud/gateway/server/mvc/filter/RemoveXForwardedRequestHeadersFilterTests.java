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

package org.springframework.cloud.gateway.server.mvc.filter;

import java.net.UnknownHostException;
import java.util.Collections;

import org.junit.jupiter.api.Test;

import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.web.servlet.function.ServerRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.cloud.gateway.server.mvc.filter.XForwardedRequestHeadersFilter.X_FORWARDED_FOR_HEADER;
import static org.springframework.cloud.gateway.server.mvc.filter.XForwardedRequestHeadersFilter.X_FORWARDED_HOST_HEADER;
import static org.springframework.cloud.gateway.server.mvc.filter.XForwardedRequestHeadersFilter.X_FORWARDED_PORT_HEADER;
import static org.springframework.cloud.gateway.server.mvc.filter.XForwardedRequestHeadersFilter.X_FORWARDED_PROTO_HEADER;

public class RemoveXForwardedRequestHeadersFilterTests {

	@Test
	public void xForwardedHeaderDoesNotExist() throws UnknownHostException {
		MockHttpServletRequest servletRequest = MockMvcRequestBuilders.get("http://localhost/get")
			.remoteAddress("10.0.0.1:80")
			.header(HttpHeaders.HOST, "myhost")
			.buildRequest(null);
		servletRequest.setRemoteHost("10.0.0.1");
		ServerRequest request = ServerRequest.create(servletRequest, Collections.emptyList());

		RemoveXForwardedRequestHeadersFilter filter = new RemoveXForwardedRequestHeadersFilter();

		HttpHeaders headers = filter.apply(request.headers().asHttpHeaders(), request);

		assertThat(headers.headerNames()).doesNotContain(X_FORWARDED_FOR_HEADER, X_FORWARDED_HOST_HEADER,
				X_FORWARDED_PORT_HEADER, X_FORWARDED_PROTO_HEADER);
	}

	@Test
	public void xForwardedHeaderExists() {
		MockHttpServletRequest servletRequest = MockMvcRequestBuilders.get("http://localhost/get")
			.remoteAddress("10.0.0.1:80")
			.header(X_FORWARDED_FOR_HEADER, "192.168.0.2")
			.header(X_FORWARDED_HOST_HEADER, "example.com")
			.header(X_FORWARDED_PORT_HEADER, "443")
			.header(X_FORWARDED_PROTO_HEADER, "https")
			.buildRequest(null);
		servletRequest.setRemoteHost("10.0.0.1");
		ServerRequest request = ServerRequest.create(servletRequest, Collections.emptyList());

		RemoveXForwardedRequestHeadersFilter filter = new RemoveXForwardedRequestHeadersFilter();

		HttpHeaders headers = filter.apply(request.headers().asHttpHeaders(), request);

		assertThat(headers.headerNames()).doesNotContain(X_FORWARDED_FOR_HEADER, X_FORWARDED_HOST_HEADER,
				X_FORWARDED_PORT_HEADER, X_FORWARDED_PROTO_HEADER);
	}

}
