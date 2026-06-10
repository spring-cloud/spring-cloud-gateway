/*
 * Copyright 2013-2025 the original author or authors.
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

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.style.ToStringCreator;
import org.springframework.validation.annotation.Validated;

/**
 * Configuration properties for Reactor Netty HttpServer.
 */
@ConfigurationProperties(HttpServerProperties.PREFIX)
@Validated
public class HttpServerProperties {

	/** Properties prefix. */
	public static final String PREFIX = GatewayProperties.PREFIX + ".httpserver";

	/** Enables Gateway Customizer for Netty HttpServer, the default is false. */
	private boolean customizerEnabled;

	/** Enables wiretap debugging for Netty HttpServer, the default is false. */
	private boolean wiretap;

	public boolean isCustomizerEnabled() {
		return customizerEnabled;
	}

	public void setCustomizerEnabled(boolean customizerEnabled) {
		this.customizerEnabled = customizerEnabled;
	}

	public boolean isWiretap() {
		return wiretap;
	}

	public void setWiretap(boolean wiretap) {
		this.wiretap = wiretap;
	}

	@Override
	public String toString() {
		// @formatter:off
		return new ToStringCreator(this)
				.append("customizerEnabled", customizerEnabled)
				.append("wiretap", wiretap)
				.toString();
		// @formatter:on

	}

}
