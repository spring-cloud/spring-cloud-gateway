/*
 * Copyright 2013-2022 the original author or authors.
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

package org.springframework.cloud.gateway.filter.factory.cache;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serial;
import java.io.Serializable;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.zip.GZIPInputStream;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.util.FileCopyUtils;

/**
 * @author Marta Medio
 * @author Ignacio Lozano
 */
public final class CachedResponse implements Serializable {

	private HttpStatusCode statusCode;

	private HttpHeaders headers;

	private List<ByteBuffer> body;

	private Date timestamp;

	private CachedResponse(HttpStatusCode statusCode, HttpHeaders headers, List<ByteBuffer> body, Date timestamp) {
		this.statusCode = statusCode;
		this.headers = headers;
		this.body = body;
		this.timestamp = timestamp;
	}

	@Serial
	private void readObject(ObjectInputStream aInputStream) throws ClassNotFoundException, IOException {
		statusCode = (HttpStatusCode) aInputStream.readObject();
		headers = (HttpHeaders) aInputStream.readObject();
		body = List.of(ByteBuffer.wrap(aInputStream.readAllBytes()).asReadOnlyBuffer());
		timestamp = (Date) aInputStream.readObject();
	}

	@Serial
	private void writeObject(ObjectOutputStream aOutputStream) throws IOException {
		aOutputStream.writeObject(statusCode);
		aOutputStream.writeObject(headers);
		aOutputStream.write(this.bodyAsByteArray());
		aOutputStream.writeObject(timestamp);
	}

	public static Builder create(HttpStatusCode statusCode) {
		return new Builder(statusCode);
	}

	public HttpStatusCode statusCode() {
		return this.statusCode;
	}

	public HttpHeaders headers() {
		return this.headers;
	}

	public List<ByteBuffer> body() {
		return Collections.unmodifiableList(body);
	}

	public Date timestamp() {
		return this.timestamp;
	}

	byte[] bodyAsByteArray() throws IOException {
		var bodyStream = new ByteArrayOutputStream();
		var channel = Channels.newChannel(bodyStream);
		for (ByteBuffer byteBuffer : body()) {
			channel.write(byteBuffer);
		}
		return bodyStream.toByteArray();
	}

	String bodyAsString() throws IOException {
		InputStream byteStream = new ByteArrayInputStream(bodyAsByteArray());
		if (headers.getOrEmpty(HttpHeaders.CONTENT_ENCODING).contains("gzip")) {
			byteStream = new GZIPInputStream(byteStream);
		}
		return new String(FileCopyUtils.copyToByteArray(byteStream));
	}

	public static class Builder {

		private final HttpStatusCode statusCode;

		private final HttpHeaders headers = new HttpHeaders();

		private final List<ByteBuffer> body = new ArrayList<>();

		private Instant timestamp;

		public Builder(HttpStatusCode statusCode) {
			this.statusCode = statusCode;
		}

		public Builder header(String name, String value) {
			this.headers.add(name, value);
			return this;
		}

		public Builder headers(HttpHeaders headers) {
			this.headers.addAll(headers);
			return this;
		}

		public Builder timestamp(Instant timestamp) {
			this.timestamp = timestamp;
			return this;
		}

		public Builder timestamp(Date timestamp) {
			this.timestamp = timestamp.toInstant();
			return this;
		}

		public Builder body(String data) {
			return appendToBody(ByteBuffer.wrap(data.getBytes(StandardCharsets.UTF_8)));
		}

		public Builder appendToBody(ByteBuffer byteBuffer) {
			this.body.add(byteBuffer);
			return this;
		}

		public CachedResponse build() {
			return new CachedResponse(statusCode, headers, body, timestamp == null ? new Date() : Date.from(timestamp));
		}

	}

}
