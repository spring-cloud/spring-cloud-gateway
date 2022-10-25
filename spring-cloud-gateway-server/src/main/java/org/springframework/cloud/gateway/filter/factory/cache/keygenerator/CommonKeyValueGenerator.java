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

package org.springframework.cloud.gateway.filter.factory.cache.keygenerator;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;

/**
 * @author Marta Medio
 */
public class CommonKeyValueGenerator implements KeyValueGenerator {

	private static final String JOINING_DELIMITER = ";";

	private final List<KeyValueGenerator> keyValueGenerators;

	public CommonKeyValueGenerator() {
		keyValueGenerators = List.of(new UriKeyValueGenerator(),
				new HeaderKeyValueGenerator(HttpHeaders.AUTHORIZATION, JOINING_DELIMITER),
				new CookiesKeyValueGenerator(JOINING_DELIMITER));
	}

	@Override
	public String apply(ServerHttpRequest request) {
		return keyValueGenerators.stream().map(generator -> generator.apply(request))
				.collect(Collectors.joining(JOINING_DELIMITER));
	}

}
