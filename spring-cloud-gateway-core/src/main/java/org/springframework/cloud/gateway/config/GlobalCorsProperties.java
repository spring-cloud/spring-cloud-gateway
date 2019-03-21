/*
 * Copyright 2013-2018 the original author or authors.
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
 *
 */

package org.springframework.cloud.gateway.config;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.gateway.handler.RoutePredicateHandlerMapping;
import org.springframework.web.cors.CorsConfiguration;

/**
 * Configuration properties for global configuration of cors. See
 * {@link RoutePredicateHandlerMapping}
 */
@ConfigurationProperties("spring.cloud.gateway.globalcors")
public class GlobalCorsProperties {

	private final Map<String, CorsConfiguration> corsConfigurations = new LinkedHashMap<>();

	public Map<String, CorsConfiguration> getCorsConfigurations() {
		return corsConfigurations;
	}

}
