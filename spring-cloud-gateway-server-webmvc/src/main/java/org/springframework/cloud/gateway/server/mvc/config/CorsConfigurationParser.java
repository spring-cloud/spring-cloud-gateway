/*
 * Copyright 2025-present the original author or authors.
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

package org.springframework.cloud.gateway.server.mvc.config;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.util.CollectionUtils;
import org.springframework.web.cors.CorsConfiguration;

/**
 * Utility class to map Gateway route CORS metadata to Spring's {@link CorsConfiguration}.
 *
 * @author Fatih Celik
 */
public abstract class CorsConfigurationParser {

	private static final String CORS_METADATA_KEY = "cors";

	private CorsConfigurationParser() {
	}

	/**
	 * Parses the route metadata map and extracts the CORS configuration if present.
	 * @param metadata the metadata map associated with a route
	 * @return an {@link Optional} containing the mapped {@link CorsConfiguration}, or
	 * empty if not found
	 */
	@SuppressWarnings("unchecked")
	public static Optional<CorsConfiguration> map(Map<String, Object> metadata) {
		if (CollectionUtils.isEmpty(metadata) || !metadata.containsKey(CORS_METADATA_KEY)) {
			return Optional.empty();
		}

		Map<String, Object> corsMetadata = (Map<String, Object>) metadata.get(CORS_METADATA_KEY);

		if (CollectionUtils.isEmpty(corsMetadata)) {
			return Optional.empty();
		}

		CorsConfiguration corsConfiguration = new CorsConfiguration();

		findValue(corsMetadata, "allowCredentials")
			.ifPresent(value -> corsConfiguration.setAllowCredentials((Boolean) value));
		findValue(corsMetadata, "allowedHeaders")
			.ifPresent(value -> corsConfiguration.setAllowedHeaders(asList(value)));
		findValue(corsMetadata, "allowedMethods")
			.ifPresent(value -> corsConfiguration.setAllowedMethods(asList(value)));
		findValue(corsMetadata, "allowedOriginPatterns")
			.ifPresent(value -> corsConfiguration.setAllowedOriginPatterns(asList(value)));
		findValue(corsMetadata, "allowedOrigins")
			.ifPresent(value -> corsConfiguration.setAllowedOrigins(asList(value)));
		findValue(corsMetadata, "exposedHeaders")
			.ifPresent(value -> corsConfiguration.setExposedHeaders(asList(value)));
		findValue(corsMetadata, "maxAge").ifPresent(value -> corsConfiguration.setMaxAge(asLong(value)));

		return Optional.of(corsConfiguration);
	}

	/**
	 * Extracts the first path pattern from the Path predicate if it exists. Defaults to
	 * "/**" if no Path predicate is found to apply CORS globally to the route.
	 * @param route the route properties to inspect
	 * @return the extracted path pattern or "/**"
	 */
	public static String extractPathPattern(RouteProperties route) {
		if (!CollectionUtils.isEmpty(route.getPredicates())) {
			for (PredicateProperties predicate : route.getPredicates()) {
				if ("Path".equalsIgnoreCase(predicate.getName()) && !CollectionUtils.isEmpty(route.getPredicates())
						&& !predicate.getArgs().isEmpty()) {
					return predicate.getArgs().values().iterator().next();
				}
			}
		}
		return "/**";
	}

	/**
	 * Safely retrieves a value from the CORS metadata map by its key.
	 * @param metadata the CORS-specific metadata map
	 * @param key the configuration key to look up (e.g., "allowedOrigins")
	 * @return an {@link Optional} containing the value, or empty if the key is missing or
	 * null
	 */
	private static Optional<Object> findValue(Map<String, Object> metadata, String key) {
		return Optional.ofNullable(metadata.get(key));
	}

	/**
	 * Converts a metadata configuration value into a List of Strings. Handles single
	 * String values and Map values.
	 * @param value the raw object value from the metadata map
	 * @return a {@link List} of string values
	 */
	@SuppressWarnings({ "unchecked", "rawtypes" })
	private static List<String> asList(Object value) {
		if (value instanceof String val) {
			return List.of(val);
		}
		if (value instanceof Map m) {
			return new ArrayList<>(m.values());
		}
		return (List<String>) value;
	}

	/**
	 * Converts a metadata configuration value into a Long. Handles Integer to Long
	 * upcasting if necessary.
	 * @param value the raw object value from the metadata map
	 * @return the numerical value represented as a Long
	 */
	private static Long asLong(Object value) {
		if (value instanceof Integer val) {
			return val.longValue();
		}
		return (Long) value;
	}

}
