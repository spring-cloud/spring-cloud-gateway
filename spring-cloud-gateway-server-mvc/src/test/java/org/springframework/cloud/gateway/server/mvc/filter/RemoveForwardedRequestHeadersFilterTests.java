package org.springframework.cloud.gateway.server.mvc.filter;

import java.util.Collections;

import org.junit.jupiter.api.Test;

import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.web.servlet.function.ServerRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.cloud.gateway.server.mvc.filter.ForwardedRequestHeadersFilter.FORWARDED_HEADER;

public class RemoveForwardedRequestHeadersFilterTests {

	@Test
	public void forwardedHeaderDoesNotExist() {
		MockHttpServletRequest servletRequest = MockMvcRequestBuilders.get("http://localhost/get")
				.remoteAddress("10.0.0.1:80")
				.header(HttpHeaders.HOST, "myhost")
				.buildRequest(null);
		servletRequest.setRemoteHost("10.0.0.1");
		ServerRequest request = ServerRequest.create(servletRequest, Collections.emptyList());

		RemoveForwardedRequestHeadersFilter filter = new RemoveForwardedRequestHeadersFilter();

		HttpHeaders headers = filter.apply(request.headers().asHttpHeaders(), request);

		assertThat(headers).doesNotContainKey(FORWARDED_HEADER);
	}

	@Test
	public void forwardedHeaderExists() {
		MockHttpServletRequest servletRequest = MockMvcRequestBuilders.get("http://localhost/get")
				.remoteAddress("10.0.0.1:80")
				.header(FORWARDED_HEADER, "for=12.34.56.78;host=example.com;proto=https, for=23.45.67.89")
				.buildRequest(null);
		servletRequest.setRemoteHost("10.0.0.1");
		ServerRequest request = ServerRequest.create(servletRequest, Collections.emptyList());

		RemoveForwardedRequestHeadersFilter filter = new RemoveForwardedRequestHeadersFilter();

		HttpHeaders headers = filter.apply(request.headers().asHttpHeaders(), request);

		assertThat(headers).doesNotContainKey(FORWARDED_HEADER);
	}
}
