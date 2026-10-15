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

package org.springframework.cloud.gateway.server.mvc.websocket;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.gateway.server.mvc.config.GatewayMvcProperties;
import org.springframework.core.style.ToStringCreator;
import org.springframework.util.unit.DataSize;

/**
 * Configuration properties for proxying WebSocket connections.
 */
@ConfigurationProperties(WebSocketProxyProperties.PREFIX)
public class WebSocketProxyProperties {

	/**
	 * WebSocketProxyProperties prefix.
	 */
	public static final String PREFIX = GatewayMvcProperties.PREFIX + ".websocket";

	/** If WebSocket proxying is enabled. */
	private boolean enabled = true;

	/**
	 * Origins allowed to open a WebSocket connection in addition to the same origin, for
	 * example https://example.com. A value of "*" allows all origins. For wildcard
	 * matching see allowedOriginPatterns. By default, only same-origin handshakes are
	 * accepted.
	 */
	private List<String> allowedOrigins = new ArrayList<>();

	/**
	 * Origin patterns allowed to open a WebSocket connection in addition to the same
	 * origin. Unlike allowedOrigins, supports wildcards such as
	 * https://*.example.com or https://example.com:[8080,8081].
	 */
	private List<String> allowedOriginPatterns = new ArrayList<>();

	/** Time allowed to connect to the backend before the handshake fails. */
	private Duration connectTimeout = Duration.ofSeconds(10);

	/** Time allowed to send a single message to either side before the session is closed. */
	private Duration sendTimeLimit = Duration.ofSeconds(10);

	/** Maximum amount of buffered outbound data per session before the session is closed. */
	private DataSize sendBufferSizeLimit = DataSize.ofKilobytes(512);

	/** Maximum size of a single message received from either side. */
	private DataSize maxMessageSize = DataSize.ofKilobytes(64);

	public boolean isEnabled() {
		return enabled;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

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

	public Duration getConnectTimeout() {
		return connectTimeout;
	}

	public void setConnectTimeout(Duration connectTimeout) {
		this.connectTimeout = connectTimeout;
	}

	public Duration getSendTimeLimit() {
		return sendTimeLimit;
	}

	public void setSendTimeLimit(Duration sendTimeLimit) {
		this.sendTimeLimit = sendTimeLimit;
	}

	public DataSize getSendBufferSizeLimit() {
		return sendBufferSizeLimit;
	}

	public void setSendBufferSizeLimit(DataSize sendBufferSizeLimit) {
		this.sendBufferSizeLimit = sendBufferSizeLimit;
	}

	public DataSize getMaxMessageSize() {
		return maxMessageSize;
	}

	public void setMaxMessageSize(DataSize maxMessageSize) {
		this.maxMessageSize = maxMessageSize;
	}

	@Override
	public String toString() {
		// @formatter:off
		return new ToStringCreator(this)
				.append("enabled", enabled)
				.append("allowedOrigins", allowedOrigins)
				.append("allowedOriginPatterns", allowedOriginPatterns)
				.append("connectTimeout", connectTimeout)
				.append("sendTimeLimit", sendTimeLimit)
				.append("sendBufferSizeLimit", sendBufferSizeLimit)
				.append("maxMessageSize", maxMessageSize)
				.toString();
		// @formatter:on
	}

}
