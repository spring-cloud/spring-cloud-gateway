/*
 * Copyright 2012-present the original author or authors.
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

package org.springframework.cloud.gateway.config;

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.style.ToStringCreator;
import org.springframework.validation.annotation.Validated;

/**
 * Configuration properties for proxying WebSocket requests.
 */
@ConfigurationProperties(WebsocketProperties.PREFIX)
@Validated
public class WebsocketProperties {

	/** Properties prefix. */
	public static final String PREFIX = GatewayProperties.PREFIX + ".websocket";

	/**
	 * Origins allowed to open a WebSocket connection in addition to the same origin. Each
	 * value is a specific origin such as https://example.com, or "*". For wildcard
	 * patterns use allowedOriginPatterns. By default, only same-origin handshakes are
	 * accepted. An origin is accepted if it matches either allowedOrigins or
	 * allowedOriginPatterns.
	 */
	private List<String> allowedOrigins = new ArrayList<>();

	/**
	 * Origin patterns allowed to open a WebSocket connection in addition to the same
	 * origin. Supports patterns such as https://*.example.com, and "*" allows all
	 * origins.
	 */
	private List<String> allowedOriginPatterns = new ArrayList<>();

	public List<String> getAllowedOrigins() {
		return allowedOrigins;
	}

	public void setAllowedOrigins(List<String> allowedOrigins) {
		this.allowedOrigins = allowedOrigins;
	}

	public List<String> getAllowedOriginPatterns() {
		return allowedOriginPatterns;
	}

	public void setAllowedOriginPatterns(List<String> allowedOriginPatterns) {
		this.allowedOriginPatterns = allowedOriginPatterns;
	}

	@Override
	public String toString() {
		return new ToStringCreator(this).append("allowedOrigins", allowedOrigins)
			.append("allowedOriginPatterns", allowedOriginPatterns)
			.toString();
	}

}
