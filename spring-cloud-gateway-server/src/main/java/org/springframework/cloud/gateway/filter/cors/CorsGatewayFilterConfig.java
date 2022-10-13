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

package org.springframework.cloud.gateway.filter.cors;

import java.util.List;
import java.util.Optional;

import org.springframework.web.cors.CorsConfiguration;

/**
 * @author Fredrich Ombico
 */
public class CorsGatewayFilterConfig {

	private final CorsConfiguration corsConfiguration;

	private String configurationString;

	public CorsGatewayFilterConfig() {
		corsConfiguration = new CorsConfiguration();
	}

	public CorsConfiguration getCorsConfiguration() {
		return corsConfiguration;
	}

	public void setCors(String configurationString) {
		this.configurationString = configurationString;
		// aligned with https://github.com/spring-cloud/spring-cloud-gateway/issues/2442
		String parsedCors = configurationString.replace("[", "").replace("]", "");

		List<String> tokens = List.of(parsedCors.split(","));

		for (String token : tokens) {
			findValue(token, "allowCredentials:")
					.ifPresent(value -> corsConfiguration.setAllowCredentials(Boolean.valueOf(value)));
			findValue(token, "allowedHeaders:")
					.ifPresent(value -> corsConfiguration.setAllowedHeaders(parseList(value)));
			findValue(token, "allowedMethods:")
					.ifPresent(value -> corsConfiguration.setAllowedMethods(parseList(value)));
			findValue(token, "allowedOriginPatterns:")
					.ifPresent(value -> corsConfiguration.setAllowedOriginPatterns(parseList(value)));
			findValue(token, "allowedOrigins:")
					.ifPresent(value -> corsConfiguration.setAllowedOrigins(parseList(value)));
			findValue(token, "exposedHeaders:")
					.ifPresent(value -> corsConfiguration.setExposedHeaders(parseList(value)));
			findValue(token, "maxAge:").ifPresent(value -> corsConfiguration.setMaxAge(Long.valueOf(value)));
		}
	}

	public String getCors() {
		return configurationString;
	}

	private Optional<String> findValue(String token, String key) {
		String value = token.startsWith(key) ? token.substring(key.length()) : null;
		return Optional.ofNullable(value);
	}

	private List<String> parseList(String value) {
		return List.of(value.split(";"));
	}

}
