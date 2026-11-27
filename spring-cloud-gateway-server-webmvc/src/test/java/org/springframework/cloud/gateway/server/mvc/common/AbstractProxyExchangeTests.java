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

package org.springframework.cloud.gateway.server.mvc.common;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import org.junit.jupiter.api.Test;

import org.springframework.cloud.gateway.server.mvc.config.GatewayMvcProperties;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.http.client.MockClientHttpResponse;
import org.springframework.web.servlet.function.ServerResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author Jens Mallien
 */
public class AbstractProxyExchangeTests {

	@Test
	public void copyResponseBodyForJson() throws IOException {
		MockClientHttpResponse mockResponse = new MockClientHttpResponse(new byte[0], 200);
		mockResponse.getHeaders().setContentType(MediaType.APPLICATION_JSON);

		InputStream inputStream = mock(InputStream.class);
		when(inputStream.transferTo(any())).thenReturn(3L);
		OutputStream outputStream = mock(OutputStream.class);

		int result = new TestProxyExchange().copyResponseBody(mockResponse, inputStream, outputStream);

		assertThat(result).isEqualTo(3);
		verify(outputStream, times(1)).flush();
	}

	@Test
	public void copyResponseBodyForTextEventStream() throws IOException {
		MockClientHttpResponse mockResponse = new MockClientHttpResponse(new byte[0], 200);
		mockResponse.getHeaders().setContentType(MediaType.TEXT_EVENT_STREAM);

		InputStream inputStream = mock(InputStream.class);
		when(inputStream.read(any())).thenReturn(1).thenReturn(1).thenReturn(1).thenReturn(-1);
		OutputStream outputStream = mock(OutputStream.class);

		int result = new TestProxyExchange().copyResponseBody(mockResponse, inputStream, outputStream);

		assertThat(result).isEqualTo(3);
		verify(outputStream, times(4)).flush();
	}

	@Test
	public void copyResponseBodyForTextEventStreamWithParameter() throws IOException {
		MockClientHttpResponse mockResponse = new MockClientHttpResponse(new byte[0], 200);
		MediaType mediaType = MediaType.parseMediaType(MediaType.TEXT_EVENT_STREAM_VALUE + ";charset=UTF-8");
		mockResponse.getHeaders().setContentType(mediaType);

		InputStream inputStream = mock(InputStream.class);
		when(inputStream.read(any())).thenReturn(1).thenReturn(1).thenReturn(1).thenReturn(-1);
		OutputStream outputStream = mock(OutputStream.class);

		int result = new TestProxyExchange().copyResponseBody(mockResponse, inputStream, outputStream);

		assertThat(result).isEqualTo(3);
		verify(outputStream, times(4)).flush();
	}

	@Test
	public void copyResponseBodyWithoutContentType() throws IOException {
		MockClientHttpResponse mockResponse = new MockClientHttpResponse(new byte[0], 200);

		InputStream inputStream = mock(InputStream.class);
		when(inputStream.transferTo(any())).thenReturn(3L);
		OutputStream outputStream = mock(OutputStream.class);

		int result = new TestProxyExchange().copyResponseBody(mockResponse, inputStream, outputStream);

		assertThat(result).isEqualTo(3);
		verify(outputStream, times(1)).flush();
	}

	/**
	 * Verifies that a non-standard charset in the response content type does not prevent
	 * the response body from being copied.
	 */
	@Test
	public void copyResponseBodyWithNonStandardCharset() throws IOException {
		MockClientHttpResponse mockResponse = new MockClientHttpResponse(new byte[0], 200);
		mockResponse.getHeaders().set(HttpHeaders.CONTENT_TYPE, "text/plain; charset=x-user-defined");

		InputStream inputStream = mock(InputStream.class);
		when(inputStream.transferTo(any())).thenReturn(3L);
		OutputStream outputStream = mock(OutputStream.class);

		int result = new TestProxyExchange().copyResponseBody(mockResponse, inputStream, outputStream);

		assertThat(result).isEqualTo(3);
		verify(outputStream, times(1)).flush();
	}

	/**
	 * Verifies that a streaming response is still detected when its content type contains
	 * a non-standard charset.
	 */
	@Test
	public void copyResponseBodyForTextEventStreamWithNonStandardCharset() throws IOException {
		MockClientHttpResponse mockResponse = new MockClientHttpResponse(new byte[0], 200);
		mockResponse.getHeaders().set(HttpHeaders.CONTENT_TYPE, "text/event-stream; charset=x-user-defined");

		InputStream inputStream = mock(InputStream.class);
		when(inputStream.read(any())).thenReturn(1).thenReturn(1).thenReturn(1).thenReturn(-1);
		OutputStream outputStream = mock(OutputStream.class);

		int result = new TestProxyExchange().copyResponseBody(mockResponse, inputStream, outputStream);

		assertThat(result).isEqualTo(3);
		verify(outputStream, times(4)).flush();
	}

	class TestProxyExchange extends AbstractProxyExchange {

		protected TestProxyExchange() {
			super(new GatewayMvcProperties());
		}

		@Override
		public ServerResponse exchange(Request request) {
			return ServerResponse.ok().build();
		}

	}

}
