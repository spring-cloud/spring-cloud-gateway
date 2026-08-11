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

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicBoolean;

import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import org.junit.jupiter.api.Test;

import org.springframework.cloud.gateway.server.mvc.config.GatewayMvcProperties;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIOException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link RestClientProxyExchange}.
 *
 * @author Goutam Adwant
 */
class RestClientProxyExchangeTests {

	@Test
	void exchangeWhenStreamingResponseCopyFailsThenDoesNotCloseClientResponse() {
		RestClient restClient = mock(RestClient.class);
		RestClient.RequestBodyUriSpec requestSpec = mock(RestClient.RequestBodyUriSpec.class);
		CloseAwareInputStream responseBody = new CloseAwareInputStream();
		TestClientHttpResponse clientResponse = new TestClientHttpResponse(responseBody);

		when(restClient.method(HttpMethod.GET)).thenReturn(requestSpec);
		when(requestSpec.uri(any(URI.class))).thenReturn(requestSpec);
		when(requestSpec.headers(any())).thenReturn(requestSpec);
		when(requestSpec.exchange(any(), eq(false))).thenAnswer((invocation) -> {
			RestClient.RequestHeadersSpec.ExchangeFunction<ServerResponse> exchangeFunction = invocation.getArgument(0);
			return exchangeFunction.exchange(mock(HttpRequest.class), clientResponse);
		});

		RestClientProxyExchange proxyExchange = new RestClientProxyExchange(restClient, new GatewayMvcProperties());
		MockHttpServletRequest servletRequest = MockMvcRequestBuilders.get("http://localhost/stream-sse-mvc")
			.buildRequest(null);
		ServerRequest serverRequest = ServerRequest.create(servletRequest, Collections.emptyList());
		ProxyExchange.Request request = proxyExchange.request(serverRequest)
			.uri(URI.create("http://localhost:8781/stream-sse-mvc"))
			.build();

		ServerResponse serverResponse = proxyExchange.exchange(request);

		assertThatIOException().isThrownBy(
				() -> serverResponse.writeTo(servletRequest, new ClientDisconnectedResponse(), Collections::emptyList))
			.withMessage("client disconnected");
		assertThat(clientResponse.closed).isFalse();
		assertThat(responseBody.closed).isTrue();
	}

	private static final class ClientDisconnectedResponse extends MockHttpServletResponse {

		private final ServletOutputStream outputStream = new ServletOutputStream() {
			@Override
			public void write(int b) {
			}

			@Override
			public void flush() throws IOException {
				throw new IOException("client disconnected");
			}

			@Override
			public boolean isReady() {
				return true;
			}

			@Override
			public void setWriteListener(WriteListener listener) {
			}
		};

		@Override
		public ServletOutputStream getOutputStream() {
			return this.outputStream;
		}

	}

	private static final class CloseAwareInputStream extends InputStream {

		private final AtomicBoolean read = new AtomicBoolean();

		private boolean closed;

		@Override
		public int read() {
			return -1;
		}

		@Override
		public int read(byte[] b, int off, int len) {
			if (this.read.compareAndSet(false, true)) {
				b[off] = 'd';
				return 1;
			}
			return -1;
		}

		@Override
		public void close() {
			this.closed = true;
		}

	}

	private static final class TestClientHttpResponse
			implements RestClient.RequestHeadersSpec.ConvertibleClientHttpResponse {

		private final CloseAwareInputStream body;

		private final HttpHeaders headers = new HttpHeaders();

		private boolean closed;

		private TestClientHttpResponse(CloseAwareInputStream body) {
			this.body = body;
			this.headers.setContentType(MediaType.TEXT_EVENT_STREAM);
		}

		@Override
		public HttpStatusCode getStatusCode() {
			return HttpStatus.OK;
		}

		@Override
		public String getStatusText() {
			return "OK";
		}

		@Override
		public HttpHeaders getHeaders() {
			return this.headers;
		}

		@Override
		public InputStream getBody() {
			return this.body;
		}

		@Override
		public void close() {
			this.closed = true;
		}

		@Override
		public <T> T bodyTo(Class<T> bodyType) {
			return null;
		}

		@Override
		public <T> T bodyTo(ParameterizedTypeReference<T> bodyType) {
			return null;
		}

		@Override
		public RestClientResponseException createException() {
			return null;
		}

	}

}
