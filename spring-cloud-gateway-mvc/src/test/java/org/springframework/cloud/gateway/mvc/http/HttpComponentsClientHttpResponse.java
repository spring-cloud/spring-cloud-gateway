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
import java.io.InputStream;

import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.util.EntityUtils;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.lang.Nullable;
import org.springframework.util.StreamUtils;

final class HttpComponentsClientHttpResponse implements ClientHttpResponse {

	private final HttpResponse response;

	@Nullable
	private HttpHeaders headers;

	HttpComponentsClientHttpResponse(final HttpResponse response) {
		this.response = response;
	}

	@Override
	public HttpStatusCode getStatusCode() throws IOException {
		return HttpStatusCode.valueOf(response.getStatusLine().getStatusCode());
	}

	@Override
	public String getStatusText() throws IOException {
		return response.getStatusLine().getReasonPhrase();
	}

	@Override
	public HttpHeaders getHeaders() {
		if (headers == null) {
			headers = new HttpHeaders();
			final Header[] responseHeaders = response.getAllHeaders();
			for (Header header : responseHeaders) {
				headers.add(header.getName(), header.getValue());
			}
		}
		return headers;
	}

	@Override
	public InputStream getBody() throws IOException {
		final HttpEntity entity = response.getEntity();
		return entity != null ? entity.getContent() : StreamUtils.emptyInput();
	}

	@Override
	public void close() {
		try {
			try {
				EntityUtils.consume(response.getEntity());
			}
			finally {
				if (response instanceof Closeable) {
					((Closeable) response).close();
				}
			}
		}
		catch (IOException ignored) {
		}
	}

}
