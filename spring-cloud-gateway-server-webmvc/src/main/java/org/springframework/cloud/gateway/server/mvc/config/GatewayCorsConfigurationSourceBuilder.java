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

import org.springframework.util.CollectionUtils;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.util.pattern.PathPatternParser;

import static org.springframework.cloud.gateway.server.mvc.config.CorsConfigurationParser.extractPathPattern;

/**
 * Builder for constructing a {@link CorsConfigurationSource} from Gateway MVC properties.
 * Uses Spring 6 {@link PathPatternParser} for modern and efficient path matching.
 *
 * @author Fatih Celik
 */
public final class GatewayCorsConfigurationSourceBuilder {

	private GatewayCorsConfigurationSourceBuilder() {
	}

	/**
	 * Builds a {@link CorsConfigurationSource} mapping route path patterns to their
	 * respective CORS configurations.
	 * @param properties the Gateway MVC properties containing route definitions
	 * @return a configured {@link CorsConfigurationSource}
	 */
	public static CorsConfigurationSource build(GatewayMvcProperties properties) {
		UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource(new PathPatternParser());

		if (!CollectionUtils.isEmpty(properties.getRoutes())) {
			for (RouteProperties route : properties.getRoutes()) {
				CorsConfigurationParser.map(route.getMetadata()).ifPresent(corsConfig -> {
					String pathPattern = extractPathPattern(route);
					source.registerCorsConfiguration(pathPattern, corsConfig);
				});
			}
		}

		return source;
	}

}
