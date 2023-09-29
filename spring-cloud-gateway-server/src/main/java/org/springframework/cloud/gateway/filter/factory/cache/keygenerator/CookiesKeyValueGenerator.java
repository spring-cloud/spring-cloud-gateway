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

import java.util.Collection;
import java.util.Objects;
import java.util.stream.Collectors;

import org.springframework.http.HttpCookie;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.util.CollectionUtils;
import org.springframework.util.MultiValueMap;

/**
 * @author Marta Medio
 * @author Ignacio Lozano
 */
class CookiesKeyValueGenerator implements KeyValueGenerator {

	private final String valueSeparator;

	CookiesKeyValueGenerator(String valueSeparator) {
		this.valueSeparator = Objects.requireNonNull(valueSeparator);
	}

	public String getKeyValue(ServerHttpRequest request) {
		String cookiesData = null;
		MultiValueMap<String, HttpCookie> cookies = request.getCookies();
		if (!CollectionUtils.isEmpty(cookies)) {
			cookiesData = cookies.values().stream().flatMap(Collection::stream)
					.map(c -> String.format("%s=%s", c.getName(), c.getValue())).sorted()
					.collect(Collectors.joining(valueSeparator));
		}
		return cookiesData;
	}

}
