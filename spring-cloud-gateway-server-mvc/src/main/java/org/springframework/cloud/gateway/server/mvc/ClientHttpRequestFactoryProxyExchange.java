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

package org.springframework.cloud.gateway.server.mvc;

import java.io.IOException;
import java.net.URI;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.ClientHttpRequest;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.util.StreamUtils;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;

public class ClientHttpRequestFactoryProxyExchange implements ProxyExchange {

	private final ClientHttpRequestFactory requestFactory;

	public ClientHttpRequestFactoryProxyExchange(ClientHttpRequestFactory requestFactory) {
		this.requestFactory = requestFactory;
	}

	@Override
	public RequestBuilder request(ServerRequest serverRequest) {
		return new ClientHttpRequestBuilder(serverRequest).method(serverRequest.method());
	}

	@Override
	public ServerResponse exchange(Request request) {
		try {
			ClientHttpRequest clientHttpRequest = requestFactory.createRequest(request.getUri(), request.getMethod());
			clientHttpRequest.getHeaders().putAll(request.getHttpHeaders());
			// TODO: copy body from request to clientHttpRequest.getBody()
			ClientHttpResponse clientHttpResponse = clientHttpRequest.execute();
			return GatewayServerResponse.status(clientHttpResponse.getStatusCode())
					.headers(httpHeaders -> httpHeaders.putAll(clientHttpResponse.getHeaders()))
					.build((req, httpServletResponse) -> {
						try {
							StreamUtils.copy(clientHttpResponse.getBody(), httpServletResponse.getOutputStream());
						}
						catch (IOException e) {
							throw new RuntimeException(e);
						}
						return null;
					});
		}
		catch (IOException e) {
			// TODO: log error?
			throw new RuntimeException(e);
		}
	}

	public static class ClientHttpRequestBuilder implements RequestBuilder, Request {

		final ServerRequest serverRequest;

		private HttpHeaders httpHeaders;

		private HttpMethod method;

		private URI uri;

		public ClientHttpRequestBuilder(ServerRequest serverRequest) {
			this.serverRequest = serverRequest;
		}

		@Override
		public RequestBuilder headers(HttpHeaders httpHeaders) {
			this.httpHeaders = httpHeaders;
			return this;
		}

		@Override
		public RequestBuilder method(HttpMethod method) {
			this.method = method;
			return this;
		}

		@Override
		public RequestBuilder uri(URI uri) {
			this.uri = uri;
			return this;
		}

		@Override
		public Request build() {
			// TODO: validation
			return this;
		}

		@Override
		public HttpHeaders getHttpHeaders() {
			return httpHeaders;
		}

		@Override
		public HttpMethod getMethod() {
			return method;
		}

		@Override
		public URI getUri() {
			return uri;
		}

		@Override
		public ServerRequest getServerRequest() {
			return serverRequest;
		}

	}

}
