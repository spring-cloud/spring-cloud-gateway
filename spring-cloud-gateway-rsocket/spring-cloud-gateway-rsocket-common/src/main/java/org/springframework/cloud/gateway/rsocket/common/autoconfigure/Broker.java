/*
 * Copyright 2013-2019 the original author or authors.
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

package org.springframework.cloud.gateway.rsocket.common.autoconfigure;

import java.net.URI;

import javax.validation.constraints.NotNull;

import org.springframework.core.style.ToStringCreator;

public class Broker {

	public enum ConnectionType {

		/** TCP RSocket connection. */
		TCP,
		/** WEBSOCKET RSocket connection. */
		WEBSOCKET

	}

	// FIXME: validate based on connectionType
	private String host;

	private int port;

	@NotNull
	private ConnectionType connectionType = ConnectionType.TCP;

	private URI wsUri;

	public String getHost() {
		return this.host;
	}

	public void setHost(String host) {
		this.host = host;
	}

	public int getPort() {
		return this.port;
	}

	public void setPort(int port) {
		this.port = port;
	}

	public ConnectionType getConnectionType() {
		return this.connectionType;
	}

	public void setConnectionType(ConnectionType connectionType) {
		this.connectionType = connectionType;
	}

	public URI getWsUri() {
		return this.wsUri;
	}

	public void setWsUri(URI wsUri) {
		this.wsUri = wsUri;
	}

	@Override
	public String toString() {
		// @formatter:off
		return new ToStringCreator(this)
				.append("host", host)
				.append("port", port)
				.append("wsUri", wsUri)
				.append("connectionType", connectionType)
				.toString();
		// @formatter:on
	}

}
