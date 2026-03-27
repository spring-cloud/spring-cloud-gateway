/*
 * Copyright 2026-present the original author or authors.
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

package org.springframework.cloud.gateway.filter.headers;

import java.net.InetSocketAddress;

import org.jspecify.annotations.Nullable;

import org.springframework.http.server.reactive.AbstractServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpRequestDecorator;

/**
 * Utility methods for forwarded headers filters.
 *
 * @author Dmitrii Grigorev
 */
public final class ForwardedHeadersFilterUtils {

	private ForwardedHeadersFilterUtils() {
	}

	/**
	 * Get the real (peer) remote address by unwrapping to the native request when
	 * possible.
	 */
	public static @Nullable InetSocketAddress extractPeerRemoteAddress(ServerHttpRequest request) {
		if (hasNativeRequest(request)) {
			try {
				ServerHttpRequest nativeRequest = ServerHttpRequestDecorator.getNativeRequest(request);
				InetSocketAddress remoteAddress = nativeRequest.getRemoteAddress();
				if (remoteAddress != null) {
					return remoteAddress;
				}
			}
			catch (RuntimeException ignored) {
				// e.g. MockServerHttpRequest extends AbstractServerHttpRequest but throws
			}
		}
		return request.getRemoteAddress();
	}

	private static boolean hasNativeRequest(ServerHttpRequest request) {
		return request instanceof ServerHttpRequestDecorator || request instanceof AbstractServerHttpRequest;
	}

}
