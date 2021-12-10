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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;

import org.springframework.http.HttpHeaders;
import org.springframework.http.client.AbstractClientHttpRequest;
import org.springframework.http.client.ClientHttpResponse;

abstract class AbstractBufferingClientHttpRequest extends AbstractClientHttpRequest {

	private ByteArrayOutputStream bufferedOutput = new ByteArrayOutputStream(1024);

	protected OutputStream getBodyInternal(HttpHeaders headers) {
		return bufferedOutput;
	}

	protected abstract ClientHttpResponse executeInternal(HttpHeaders headers, byte[] body) throws IOException;

	protected ClientHttpResponse executeInternal(HttpHeaders headers) throws IOException {
		final byte[] bytes = bufferedOutput.toByteArray();
		if (headers.getContentLength() < 0L) {
			headers.setContentLength(bytes.length);
		}

		final ClientHttpResponse response = executeInternal(headers, bytes);
		bufferedOutput = new ByteArrayOutputStream(0);
		return response;
	}

}
