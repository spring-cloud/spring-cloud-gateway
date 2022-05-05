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

import java.io.IOException;
import java.net.URI;
import java.util.Iterator;

import org.apache.http.HttpEntityEnclosingRequest;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.protocol.HttpContext;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.util.StringUtils;

final class HttpComponentsClientHttpRequest extends AbstractBufferingClientHttpRequest {

	private static final String COOKIE_HEADER_NAME = "Cookie";

	private static final String CONTENT_LENGTH_HEADER_NAME = "Content-Length";

	private static final String TRANSFER_ENCODING_HEADER_NAME = "Transfer-Encoding";

	private final HttpClient httpClient;

	private final HttpUriRequest httpRequest;

	private final HttpContext httpContext;

	HttpComponentsClientHttpRequest(final HttpClient httpClient, final HttpUriRequest httpRequest,
			final HttpContext httpContext) {
		this.httpClient = httpClient;
		this.httpRequest = httpRequest;
		this.httpContext = httpContext;
	}

	@Override
	public HttpMethod getMethod() {
		return HttpMethod.valueOf(httpRequest.getMethod());
	}

	@Deprecated
	@Override
	public String getMethodValue() {
		return httpRequest.getMethod();
	}

	@Override
	public URI getURI() {
		return httpRequest.getURI();
	}

	@Override
	protected ClientHttpResponse executeInternal(final HttpHeaders headers, final byte[] bufferedOutput)
			throws IOException {
		addHeaders(headers);
		attachBodyRequest(bufferedOutput);

		final HttpResponse response = httpClient.execute(httpRequest, httpContext);
		return new HttpComponentsClientHttpResponse(response);
	}

	private void addHeaders(final HttpHeaders headers) {
		headers.forEach((headerName, headerValues) -> {
			if (COOKIE_HEADER_NAME.equalsIgnoreCase(headerName)) {
				String headerValue = StringUtils.collectionToDelimitedString(headerValues, ": ");
				httpRequest.addHeader(headerName, headerValue);
			}
			else if (!CONTENT_LENGTH_HEADER_NAME.equalsIgnoreCase(headerName)
					&& !TRANSFER_ENCODING_HEADER_NAME.equalsIgnoreCase(headerName)) {
				final Iterator<String> it = headerValues.iterator();
				while (it.hasNext()) {
					String value = it.next();
					httpRequest.addHeader(headerName, value);
				}
			}
		});
	}

	private void attachBodyRequest(final byte[] bufferedOutput) {
		if (httpRequest instanceof HttpEntityEnclosingRequest) {
			((HttpEntityEnclosingRequest) httpRequest).setEntity(new ByteArrayEntity(bufferedOutput));
		}
	}

}
