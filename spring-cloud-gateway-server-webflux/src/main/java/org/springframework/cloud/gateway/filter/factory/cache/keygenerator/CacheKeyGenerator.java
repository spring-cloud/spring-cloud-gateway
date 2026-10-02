/*
 * Copyright 2013-present the original author or authors.
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
import java.util.function.Function;
import java.util.stream.Stream;

import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.server.ServerWebExchange;

/**
 * @author Marta Medio
 * @author Ignacio Lozano
 * @author Simone Gerevini
 */
public class CacheKeyGenerator {

	/* for testing */ static final String KEY_SEPARATOR = ";";

	private static final byte[] KEY_SEPARATOR_BYTES = KEY_SEPARATOR.getBytes();

	private final ThreadLocal<MessageDigest> messageDigest;

	/* for testing */ static final List<KeyValueGenerator> DEFAULT_KEY_VALUE_GENERATORS = List.of(
			new RouteIdKeyValueGenerator(), new UriKeyValueGenerator(),
			new HeaderKeyValueGenerator(HttpHeaders.AUTHORIZATION, KEY_SEPARATOR),
			new CookiesKeyValueGenerator(KEY_SEPARATOR));

	public CacheKeyGenerator() {
		messageDigest = ThreadLocal.withInitial(() -> {
			try {
				return MessageDigest.getInstance("MD5");
			}
			catch (NoSuchAlgorithmException e) {
				throw new RuntimeException("Error creating CacheKeyGenerator", e);
			}
		});
	}

	/**
	 * Generates the metadata key for a request.
	 * @param request the request
	 * @param varyHeaders the headers the response varies on
	 * @return the metadata key
	 * @deprecated in favor of {@link #generateMetadataKey(ServerWebExchange, String...)},
	 * which also scopes the key to the matched route
	 */
	@Deprecated
	public String generateMetadataKey(ServerHttpRequest request, String... varyHeaders) {
		return "META_" + generateKey(request, varyHeaders);
	}

	public String generateMetadataKey(ServerWebExchange exchange, String... varyHeaders) {
		return "META_" + generateKey(exchange, varyHeaders);
	}

	/**
	 * Generates the cache key for a request.
	 * @param request the request
	 * @param varyHeaders the headers the response varies on
	 * @return the cache key
	 * @deprecated in favor of {@link #generateKey(ServerWebExchange, String...)}, which
	 * also scopes the key to the matched route
	 */
	@Deprecated
	public String generateKey(ServerHttpRequest request, String... varyHeaders) {
		return generateKey(request, varyHeaders != null ? Arrays.asList(varyHeaders) : Collections.emptyList());
	}

	public String generateKey(ServerWebExchange exchange, String... varyHeaders) {
		return generateKey(exchange, varyHeaders != null ? Arrays.asList(varyHeaders) : Collections.emptyList());
	}

	/**
	 * Generates the cache key for a request.
	 * @param request the request
	 * @param varyHeaders the headers the response varies on
	 * @return the cache key
	 * @deprecated in favor of {@link #generateKey(ServerWebExchange, List)}, which also
	 * scopes the key to the matched route
	 */
	@Deprecated
	public String generateKey(ServerHttpRequest request, List<String> varyHeaders) {
		return generateKey(generator -> generator.apply(request), varyHeaders);
	}

	public String generateKey(ServerWebExchange exchange, List<String> varyHeaders) {
		return generateKey(generator -> generator.apply(exchange), varyHeaders);
	}

	private String generateKey(Function<KeyValueGenerator, String> valueOf, List<String> varyHeaders) {
		byte[] rawKey = generateRawKey(valueOf, varyHeaders);
		byte[] digest = messageDigest.get().digest(rawKey);

		return Base64.getEncoder().encodeToString(digest);
	}

	private byte[] generateRawKey(Function<KeyValueGenerator, String> valueOf, List<String> varyHeaders) {
		Stream<KeyValueGenerator> keyValueGenerators = Stream.concat(DEFAULT_KEY_VALUE_GENERATORS.stream(),
				varyHeaders.stream().sorted().map(header -> new HeaderKeyValueGenerator(header, ",")));

		final ByteArrayOutputStream byteOutputStream = new ByteArrayOutputStream();
		keyValueGenerators.map(valueOf).map(String::getBytes).forEach(bytes -> {
			byteOutputStream.writeBytes(bytes);
			byteOutputStream.writeBytes(KEY_SEPARATOR_BYTES);
		});

		return byteOutputStream.toByteArray();
	}

}
