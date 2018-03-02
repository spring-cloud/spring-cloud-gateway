/*
 * Copyright 2013-2018 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package org.springframework.cloud.gateway.fn;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRange;
import org.springframework.http.MediaType;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.server.ServerRequest;

import java.net.InetSocketAddress;
import java.nio.charset.Charset;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;

public class MutableHttpHeaders implements ServerRequest.Headers {
	private HttpHeaders headers;

	public MutableHttpHeaders(HttpHeaders original) {
		headers = new HttpHeaders();
		copyMultiValueMap(original, headers);
	}

	private static <K, V> void copyMultiValueMap(MultiValueMap<K,V> source,
												 MultiValueMap<K,V> destination) {

		for (Map.Entry<K, List<V>> entry : source.entrySet()) {
			K key = entry.getKey();
			List<V> values = new LinkedList<>(entry.getValue());
			destination.put(key, values);
		}
	}

	@Override
	public List<MediaType> accept() {
		return headers.getAccept();
	}

	@Override
	public List<Charset> acceptCharset() {
		return headers.getAcceptCharset();
	}

	@Override
	public List<Locale.LanguageRange> acceptLanguage() {
		return headers.getAcceptLanguage();
	}

	@Override
	public OptionalLong contentLength() {
		long value = headers.getContentLength();
		return (value != -1 ? OptionalLong.of(value) : OptionalLong.empty());
	}

	@Override
	public Optional<MediaType> contentType() {
		return Optional.ofNullable(headers.getContentType());
	}

	@Override
	public InetSocketAddress host() {
		return headers.getHost();
	}

	@Override
	public List<HttpRange> range() {
		return headers.getRange();
	}

	@Override
	public List<String> header(String headerName) {
		return headers.get(headerName);
	}

	@Override
	public HttpHeaders asHttpHeaders() {
		return headers;
	}
}
