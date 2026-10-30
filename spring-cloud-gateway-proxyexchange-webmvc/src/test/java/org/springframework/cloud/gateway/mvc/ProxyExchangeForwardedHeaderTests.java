/*
 * Copyright 2016-present the original author or authors.
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

package org.springframework.cloud.gateway.mvc;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.ByteArrayHttpMessageConverter;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.bind.support.DefaultDataBinderFactory;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.method.support.ModelAndViewContainer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests for how {@link ProxyExchange} computes the {@code Forwarded} header, in
 * particular when the request uses HTTP/2 and therefore carries no {@code Host} header.
 *
 * @author henriquejsza
 */
class ProxyExchangeForwardedHeaderTests {

	@SuppressWarnings({ "unchecked", "rawtypes" })
	private String forwardedHeaderFor(MockHttpServletRequest request) {
		RestTemplate rest = mock(RestTemplate.class);
		when(rest.getMessageConverters()).thenReturn(List.of(new ByteArrayHttpMessageConverter()));
		ArgumentCaptor<RequestEntity> captor = ArgumentCaptor.forClass(RequestEntity.class);
		when(rest.exchange(captor.capture(), any(ParameterizedTypeReference.class)))
			.thenReturn(ResponseEntity.ok().build());

		ProxyExchange<Object> proxy = new ProxyExchange<>(rest, new ServletWebRequest(request),
				new ModelAndViewContainer(), new DefaultDataBinderFactory(null), Object.class);
		ResponseEntity<Object> response = proxy.uri("http://backend/foo").get();

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		return captor.getValue().getHeaders().getFirst("forwarded");
	}

	@Test
	void forwardedHeaderUsesHostHeaderWhenPresent() {
		MockHttpServletRequest request = new MockHttpServletRequest("GET", "/proxy");
		request.setScheme("http");
		request.setServerName("localhost");
		request.setServerPort(8080);
		request.addHeader("host", "example.com:9000");

		assertThat(forwardedHeaderFor(request)).isEqualTo("host=example.com:9000");
	}

	@Test
	void forwardedHeaderUsesAuthorityWhenHostHeaderIsMissingOverHttp2() {
		// Simulate an HTTP/2 request: no Host header, the authority (:authority
		// pseudo-header) is exposed through the server name and port.
		MockHttpServletRequest request = new MockHttpServletRequest("GET", "/proxy");
		request.setScheme("https");
		request.setServerName("localhost");
		request.setServerPort(8443);

		assertThat(forwardedHeaderFor(request)).isEqualTo("host=localhost:8443;proto=https");
	}

}
