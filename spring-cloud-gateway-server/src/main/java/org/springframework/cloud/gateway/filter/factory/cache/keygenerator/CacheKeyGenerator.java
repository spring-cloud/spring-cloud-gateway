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

import java.io.ByteArrayOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

import org.springframework.http.server.reactive.ServerHttpRequest;

/**
 * @author Marta Medio
 * @author Ignacio Lozano
 */
public class CacheKeyGenerator {

	private static final byte[] KEY_SEPARATOR_BYTES = ";".getBytes();

	private final MessageDigest messageDigest;

	private static final CommonKeyValueGenerator COMMON_KEY_VALUE_GENERATOR = new CommonKeyValueGenerator();

	public CacheKeyGenerator() {
		try {
			messageDigest = MessageDigest.getInstance("MD5");
		}
		catch (NoSuchAlgorithmException e) {
			throw new RuntimeException("Error creating CacheKeyGenerator", e);
		}
	}

	public String generateMetadataKey(ServerHttpRequest request, String... varyHeaders) {
		return "META_" + generateKey(request, varyHeaders);
	}

	public String generateKey(ServerHttpRequest request, String... varyHeaders) {
		return generateKey(request, varyHeaders != null ? Arrays.asList(varyHeaders) : Collections.emptyList());
	}

	public String generateKey(ServerHttpRequest request, List<String> varyHeaders) {
		byte[] rawKey = generateRawKey(request, varyHeaders);
		byte[] digest = messageDigest.digest(rawKey);

		return Base64.getEncoder().encodeToString(digest);
	}

	private Stream<KeyValueGenerator> getKeyValueGenerators(List<String> varyHeaders) {
		return Stream.concat(Stream.of(COMMON_KEY_VALUE_GENERATOR),
				varyHeaders.stream().sorted().map(header -> new HeaderKeyValueGenerator(header, ",")));
	}

	private byte[] generateRawKey(ServerHttpRequest request, List<String> varyHeaders) {
		Stream<KeyValueGenerator> keyValueGenerators = getKeyValueGenerators(varyHeaders);

		final ByteArrayOutputStream byteOutputStream = new ByteArrayOutputStream();
		keyValueGenerators.map(generator -> generator.apply(request)).map(String::getBytes).forEach(bytes -> {
			byteOutputStream.writeBytes(bytes);
			byteOutputStream.writeBytes(KEY_SEPARATOR_BYTES);
		});

		return byteOutputStream.toByteArray();
	}

}
