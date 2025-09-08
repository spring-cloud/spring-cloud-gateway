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

/*
 * Copyright (c) 2020-2023 VMware, Inc. or its affiliates, All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.cloud.gateway.config;

import java.util.function.BiFunction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.netty.handler.codec.http.HttpRequest;
import reactor.netty.http.server.ConnectionInfo;
import reactor.netty.transport.AddressUtils;

import static reactor.netty.http.server.ConnectionInfo.getDefaultHostPort;

/**
 * Default implementation for handling {@code X-Forwarded}/{@code Forwarded} headers.
 *
 * @author Andrey Shlykov
 * @since 0.9.12
 */
final class DefaultNettyHttpForwardedHeaderHandler implements BiFunction<ConnectionInfo, HttpRequest, ConnectionInfo> {

	static final DefaultNettyHttpForwardedHeaderHandler INSTANCE = new DefaultNettyHttpForwardedHeaderHandler();

	static final String FORWARDED_HEADER = "Forwarded";
	static final String X_FORWARDED_IP_HEADER = "X-Forwarded-For";
	static final String X_FORWARDED_HOST_HEADER = "X-Forwarded-Host";
	static final String X_FORWARDED_PORT_HEADER = "X-Forwarded-Port";
	static final String X_FORWARDED_PROTO_HEADER = "X-Forwarded-Proto";

	static final Pattern FORWARDED_HOST_PATTERN = Pattern.compile("host=\"?([^;,\"]+)\"?");
	static final Pattern FORWARDED_PROTO_PATTERN = Pattern.compile("proto=\"?([^;,\"]+)\"?");
	static final Pattern FORWARDED_FOR_PATTERN = Pattern.compile("for=\"?([^;,\"]+)\"?");

	/**
	 * Specifies whether the Http Server applies a strict {@code Forwarded} header
	 * validation. By default, it is enabled and strict validation is used.
	 * @since 1.0.8
	 * @deprecated The system property is used for backwards compatibility and will be
	 * removed in version 1.2.0.
	 */
	@Deprecated
	static final String FORWARDED_HEADER_VALIDATION = "reactor.netty.http.server.forwarded.strictValidation";
	static final boolean DEFAULT_FORWARDED_HEADER_VALIDATION = Boolean
		.parseBoolean(System.getProperty(FORWARDED_HEADER_VALIDATION, "true"));

	@Override
	public ConnectionInfo apply(ConnectionInfo connectionInfo, HttpRequest request) {
		String forwardedHeader = request.headers().get(FORWARDED_HEADER);
		if (forwardedHeader != null) {
			return parseForwardedInfo(connectionInfo, forwardedHeader);
		}
		return parseXForwardedInfo(connectionInfo, request);
	}

	private ConnectionInfo parseForwardedInfo(ConnectionInfo connectionInfo, String forwardedHeader) {
		String forwarded = forwardedHeader.split(",", 2)[0];
		Matcher protoMatcher = FORWARDED_PROTO_PATTERN.matcher(forwarded);
		if (protoMatcher.find()) {
			connectionInfo = connectionInfo.withScheme(protoMatcher.group(1).trim());
		}
		Matcher hostMatcher = FORWARDED_HOST_PATTERN.matcher(forwarded);
		if (hostMatcher.find()) {
			connectionInfo = connectionInfo.withHostAddress(AddressUtils.parseAddress(hostMatcher.group(1),
					getDefaultHostPort(connectionInfo.getScheme()), DEFAULT_FORWARDED_HEADER_VALIDATION));
		}
		Matcher forMatcher = FORWARDED_FOR_PATTERN.matcher(forwarded);
		if (forMatcher.find()) {
			connectionInfo = connectionInfo.withRemoteAddress(AddressUtils.parseAddress(forMatcher.group(1).trim(),
					connectionInfo.getRemoteAddress().getPort(), DEFAULT_FORWARDED_HEADER_VALIDATION));
		}
		return connectionInfo;
	}

	private ConnectionInfo parseXForwardedInfo(ConnectionInfo connectionInfo, HttpRequest request) {
		String ipHeader = request.headers().get(X_FORWARDED_IP_HEADER);
		if (ipHeader != null) {
			connectionInfo = connectionInfo.withRemoteAddress(
					AddressUtils.parseAddress(ipHeader.split(",", 2)[0], connectionInfo.getRemoteAddress().getPort()));
		}
		String protoHeader = request.headers().get(X_FORWARDED_PROTO_HEADER);
		if (protoHeader != null) {
			connectionInfo = connectionInfo.withScheme(protoHeader.split(",", 2)[0].trim());
		}
		String hostHeader = request.headers().get(X_FORWARDED_HOST_HEADER);
		if (hostHeader != null) {
			connectionInfo = connectionInfo
				.withHostAddress(AddressUtils.parseAddress(hostHeader.split(",", 2)[0].trim(),
						getDefaultHostPort(connectionInfo.getScheme()), DEFAULT_FORWARDED_HEADER_VALIDATION));
		}

		String portHeader = request.headers().get(X_FORWARDED_PORT_HEADER);
		if (portHeader != null && !portHeader.isEmpty()) {
			String portStr = portHeader.split(",", 2)[0].trim();
			if (portStr.chars().allMatch(Character::isDigit)) {
				int port = Integer.parseInt(portStr);
				connectionInfo = connectionInfo.withHostAddress(
						AddressUtils.createUnresolved(connectionInfo.getHostAddress().getHostString(), port),
						connectionInfo.getHostName(), port);
			}
			else if (DEFAULT_FORWARDED_HEADER_VALIDATION) {
				throw new IllegalArgumentException("Failed to parse a port from " + portHeader);
			}
		}
		return connectionInfo;
	}

}
