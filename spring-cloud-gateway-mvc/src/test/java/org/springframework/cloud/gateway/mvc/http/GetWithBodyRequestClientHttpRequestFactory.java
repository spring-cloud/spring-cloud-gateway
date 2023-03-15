/*
 * Copyright 2016-2019 the original author or authors.
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

package org.springframework.cloud.gateway.mvc.http;

import java.io.Closeable;
import java.io.IOException;
import java.net.URI;

import org.apache.http.client.HttpClient;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.Configurable;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpEntityEnclosingRequestBase;
import org.apache.http.client.methods.HttpHead;
import org.apache.http.client.methods.HttpOptions;
import org.apache.http.client.methods.HttpPatch;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.methods.HttpTrace;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.protocol.HttpContext;

import org.springframework.beans.factory.DisposableBean;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.ClientHttpRequest;
import org.springframework.http.client.ClientHttpRequestFactory;

public class GetWithBodyRequestClientHttpRequestFactory implements ClientHttpRequestFactory, DisposableBean {

	private final HttpClient httpClient;

	public GetWithBodyRequestClientHttpRequestFactory() {
		this.httpClient = HttpClients.createSystem();
	}

	@Override
	public ClientHttpRequest createRequest(final URI uri, final HttpMethod httpMethod) throws IOException {
		final HttpUriRequest httpRequest = createHttpUriRequest(httpMethod, uri);
		final HttpContext context = HttpClientContext.create();

		if (context.getAttribute("http.request-config") == null) {
			RequestConfig config = null;
			if (httpRequest instanceof Configurable) {
				config = ((Configurable) httpRequest).getConfig();
			}
			if (config == null) {
				config = createRequestConfig(httpClient);
			}
			if (config != null) {
				context.setAttribute("http.request-config", config);
			}
		}

		return new HttpComponentsClientHttpRequest(httpClient, httpRequest, context);
	}

	private HttpUriRequest createHttpUriRequest(final HttpMethod httpMethod, final URI uri) {
		if (httpMethod.equals(HttpMethod.GET)) {
			return new GetWithEntity(uri);
		}
		else if (httpMethod.equals(HttpMethod.HEAD)) {
			return new HttpHead(uri);
		}
		else if (httpMethod.equals(HttpMethod.OPTIONS)) {
			return new HttpOptions(uri);
		}
		else if (httpMethod.equals(HttpMethod.POST)) {
			return new HttpPost(uri);
		}
		else if (httpMethod.equals(HttpMethod.DELETE)) {
			return new HttpDelete(uri);
		}
		else if (httpMethod.equals(HttpMethod.PUT)) {
			return new HttpPut(uri);
		}
		else if (httpMethod.equals(HttpMethod.PATCH)) {
			return new HttpPatch(uri);
		}
		else if (httpMethod.equals(HttpMethod.TRACE)) {
			return new HttpTrace(uri);
		}

		throw new IllegalArgumentException("Invalid HTTP method: " + httpMethod);
	}

	private RequestConfig createRequestConfig(final HttpClient client) {
		if (client instanceof Configurable) {
			return ((Configurable) client).getConfig();
		}
		return null;
	}

	@Override
	public void destroy() throws Exception {
		if (httpClient instanceof Closeable) {
			((Closeable) httpClient).close();
		}
	}

	public static class GetWithEntity extends HttpEntityEnclosingRequestBase {

		public static final String METHOD_NAME = "GET";

		public GetWithEntity(final URI uri) {
			setURI(uri);
		}

		@Override
		public String getMethod() {
			return METHOD_NAME;
		}

	}

}
