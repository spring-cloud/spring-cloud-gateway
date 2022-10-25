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

package org.springframework.cloud.gateway.filter.factory.cache.keygenerator;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.util.StringUtils;

/**
 * @author Marta Medio
 * @author Ignacio Lozano
 */
class HeaderKeyValueGenerator implements KeyValueGenerator {

	private final String header;

	private final String valueSeparator;

	HeaderKeyValueGenerator(String header, String valueSeparator) {
		this.valueSeparator = valueSeparator;
		if (!StringUtils.hasText(header)) {
			throw new IllegalArgumentException("The parameter cannot be empty or null");
		}
		this.header = header;
	}

	@Override
	public String getKeyValue(ServerHttpRequest request) {
		HttpHeaders headers = request.getHeaders();
		if (headers.get(header) != null) {
			StringBuilder keyVaryHeaders = new StringBuilder();
			keyVaryHeaders.append(header).append("=")
					.append(getHeaderValues(headers).sorted().collect(Collectors.joining(valueSeparator)));
			return keyVaryHeaders.toString();
		}
		return null;
	}

	private Stream<String> getHeaderValues(HttpHeaders headers) {
		List<String> value = headers.get(header);
		return value == null ? Stream.empty() : value.stream();
	}

}
