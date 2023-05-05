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

import java.net.URI;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;

public class RestTemplateProxyExchange implements ProxyExchange {

	private final RestTemplate restTemplate;

	public RestTemplateProxyExchange(RestTemplate restTemplate) {
		this.restTemplate = restTemplate;
	}

	@Override
	public RequestBuilder request(ServerRequest serverRequest) {
		return new RestTemplateRequestBuilder(serverRequest).method(serverRequest.method());
	}

	@Override
	public ServerResponse exchange(Request request) {
		RequestEntity<Void> entity = RequestEntity.method(request.getMethod(), request.getUri())
				.headers(request.getHttpHeaders()).build();
		ResponseEntity<Object> response = restTemplate.exchange(entity, Object.class);
		return ServerResponse.status(response.getStatusCode())
				.headers(httpHeaders -> httpHeaders.putAll(response.getHeaders())).body(response.getBody());
	}

	public static class RestTemplateRequestBuilder implements RequestBuilder, Request {

		final ServerRequest serverRequest;

		private HttpHeaders httpHeaders;

		private HttpMethod method;

		private URI uri;

		public RestTemplateRequestBuilder(ServerRequest serverRequest) {
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
