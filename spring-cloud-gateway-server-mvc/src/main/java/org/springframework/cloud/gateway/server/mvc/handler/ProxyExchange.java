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

package org.springframework.cloud.gateway.server.mvc.handler;

import java.net.URI;
import java.util.function.BiFunction;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;

public interface ProxyExchange {

	default RequestBuilder request(ServerRequest serverRequest) {
		return new DefaultRequestBuilder(serverRequest);
	}

	ServerResponse exchange(Request request);

	interface Request {

		HttpHeaders getHttpHeaders();

		HttpMethod getMethod();

		URI getUri();

		ServerRequest getServerRequest();

		BiFunction<HttpHeaders, ServerResponse, HttpHeaders> getResponseHeadersFilter();

	}

	interface RequestBuilder {

		RequestBuilder headers(HttpHeaders httpHeaders);

		RequestBuilder method(HttpMethod method);

		RequestBuilder uri(URI uri);

		RequestBuilder responseHeadersFilter(
				BiFunction<HttpHeaders, ServerResponse, HttpHeaders> responseHeadersFilter);

		Request build();

	}

	class DefaultRequestBuilder implements RequestBuilder, Request {

		final ServerRequest serverRequest;

		private HttpHeaders httpHeaders;

		private HttpMethod method;

		private URI uri;

		private BiFunction<HttpHeaders, ServerResponse, HttpHeaders> responseHeadersFilter;

		DefaultRequestBuilder(ServerRequest serverRequest) {
			this.serverRequest = serverRequest;
			this.method = serverRequest.method();
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
		public RequestBuilder responseHeadersFilter(
				BiFunction<HttpHeaders, ServerResponse, HttpHeaders> responseHeadersFilter) {
			this.responseHeadersFilter = responseHeadersFilter;
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

		public BiFunction<HttpHeaders, ServerResponse, HttpHeaders> getResponseHeadersFilter() {
			return responseHeadersFilter;
		}

	}

}
