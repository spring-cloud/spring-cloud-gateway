/*
 * Copyright 2013-2020 the original author or authors.
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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.zip.GZIPOutputStream;

import org.junit.jupiter.api.Test;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Ignacio Lozano
 */
class CachedResponseTest {

	@Test
	void bodyAsByteArray_whenEmptyBody() throws IOException {
		CachedResponse cachedResponse = CachedResponse.create(HttpStatus.OK).build();

		byte[] asByteArray = cachedResponse.bodyAsByteArray();

		assertThat(asByteArray).isEmpty();
	}

	@Test
	void bodyAsByteArray_whenThereIsContent() throws IOException {
		String body = "example";
		CachedResponse cachedResponse = CachedResponse.create(HttpStatus.OK).body(body).build();

		byte[] asByteArray = cachedResponse.bodyAsByteArray();

		assertThat(asByteArray).isEqualTo(body.getBytes());
	}

	@Test
	void bodyAsString_whenEmptyBody() throws IOException {
		CachedResponse cachedResponse = CachedResponse.create(HttpStatus.OK).build();

		String asString = cachedResponse.bodyAsString();

		assertThat(asString).isEmpty();
	}

	@Test
	void bodyAsString_whenThereIsContent() throws IOException {
		String body = "example";
		CachedResponse cachedResponse = CachedResponse.create(HttpStatus.OK).body(body).build();

		String asString = cachedResponse.bodyAsString();

		assertThat(asString).isEqualTo(body);
	}

	@Test
	void bodyAsString_whenThereIsGZipContent() throws IOException {
		String body = "example";
		CachedResponse cachedResponse = CachedResponse.create(HttpStatus.OK)
				.header(HttpHeaders.CONTENT_ENCODING, "gzip").appendToBody(convertToGzip(body)).build();

		String asString = cachedResponse.bodyAsString();

		assertThat(asString).isEqualTo(body);
	}

	private ByteBuffer convertToGzip(String str) throws IOException {
		var outBytes = new ByteArrayOutputStream();
		var outGzip = new GZIPOutputStream(outBytes);
		outGzip.write(str.getBytes());
		outGzip.close();
		return ByteBuffer.wrap(outBytes.toByteArray());
	}

}
