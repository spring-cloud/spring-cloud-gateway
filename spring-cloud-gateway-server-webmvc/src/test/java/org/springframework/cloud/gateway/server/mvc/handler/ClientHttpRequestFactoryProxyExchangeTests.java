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

package org.springframework.cloud.gateway.server.mvc.handler;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Collections;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.cloud.gateway.server.mvc.config.GatewayMvcProperties;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequest;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link ClientHttpRequestFactoryProxyExchange}.
 *
 * @author bwang
 */
class ClientHttpRequestFactoryProxyExchangeTests {

	private final ClientHttpResponse clientResponse = mock(ClientHttpResponse.class);

	private ClientHttpRequestFactoryProxyExchange proxyExchange;

	@BeforeEach
	void setUp() throws IOException {
		HttpHeaders responseHeaders = new HttpHeaders();
		responseHeaders.setContentType(MediaType.TEXT_PLAIN);
		responseHeaders.setETag("\"v1\"");
		when(this.clientResponse.getStatusCode()).thenReturn(HttpStatus.OK);
		when(this.clientResponse.getHeaders()).thenReturn(responseHeaders);
		when(this.clientResponse.getBody())
			.thenReturn(new ByteArrayInputStream("data".getBytes(StandardCharsets.UTF_8)));

		ClientHttpRequest clientRequest = mock(ClientHttpRequest.class);
		when(clientRequest.getHeaders()).thenReturn(new HttpHeaders());
		when(clientRequest.getBody()).thenReturn(new ByteArrayOutputStream());
		when(clientRequest.execute()).thenReturn(this.clientResponse);
		ClientHttpRequestFactory requestFactory = mock(ClientHttpRequestFactory.class);
		when(requestFactory.createRequest(any(URI.class), eq(HttpMethod.GET))).thenReturn(clientRequest);

		this.proxyExchange = new ClientHttpRequestFactoryProxyExchange(requestFactory, new GatewayMvcProperties());
	}

	@Test
	void writeToWhenBodyWrittenThenClosesClientResponseOnce() throws Exception {
		MockHttpServletRequest servletRequest = MockMvcRequestBuilders.get("http://localhost/resource")
			.buildRequest(null);

		ServerResponse serverResponse = exchange(servletRequest,
				ClientHttpRequestFactoryProxyExchangeTests::copyHeaders);
		MockHttpServletResponse servletResponse = new MockHttpServletResponse();
		serverResponse.writeTo(servletRequest, servletResponse, Collections::emptyList);

		assertThat(servletResponse.getStatus()).isEqualTo(HttpStatus.OK.value());
		assertThat(servletResponse.getContentAsString()).isEqualTo("data");
		verify(this.clientResponse, times(1)).close();
	}

	@Test
	void writeToWhenNotModifiedThenClosesClientResponse() throws Exception {
		MockHttpServletRequest servletRequest = MockMvcRequestBuilders.get("http://localhost/resource")
			.header(HttpHeaders.IF_NONE_MATCH, "\"v1\"")
			.buildRequest(null);

		ServerResponse serverResponse = exchange(servletRequest,
				ClientHttpRequestFactoryProxyExchangeTests::copyHeaders);
		MockHttpServletResponse servletResponse = new MockHttpServletResponse();
		serverResponse.writeTo(servletRequest, servletResponse, Collections::emptyList);

		assertThat(servletResponse.getStatus()).isEqualTo(HttpStatus.NOT_MODIFIED.value());
		verify(this.clientResponse, times(1)).close();
	}

	@Test
	void exchangeWhenResponseConsumerFailsThenClosesClientResponse() throws IOException {
		MockHttpServletRequest servletRequest = MockMvcRequestBuilders.get("http://localhost/resource")
			.buildRequest(null);

		assertThatIllegalStateException().isThrownBy(() -> exchange(servletRequest, (response, sr) -> {
			throw new IllegalStateException("consumer failed");
		})).withMessage("consumer failed");
		verify(this.clientResponse, times(1)).close();
	}

	private ServerResponse exchange(MockHttpServletRequest servletRequest,
			ProxyExchange.ResponseConsumer responseConsumer) {
		ServerRequest serverRequest = ServerRequest.create(servletRequest, Collections.emptyList());
		ProxyExchange.Request request = this.proxyExchange.request(serverRequest)
			.uri(URI.create("http://localhost:8781/resource"))
			.responseConsumer(responseConsumer)
			.build();
		return this.proxyExchange.exchange(request);
	}

	private static void copyHeaders(ProxyExchange.Response response, ServerResponse serverResponse) {
		serverResponse.headers().putAll(response.getHeaders());
	}

}
