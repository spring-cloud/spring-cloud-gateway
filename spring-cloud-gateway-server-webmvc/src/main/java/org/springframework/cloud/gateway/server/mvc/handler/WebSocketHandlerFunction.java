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

package org.springframework.cloud.gateway.server.mvc.handler;

import java.util.Locale;

import org.springframework.http.HttpHeaders;
import org.springframework.web.servlet.function.HandlerFunction;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;

/**
 * {@link HandlerFunction} that proxies WebSocket connections. Kept free of
 * {@code spring-websocket} types so that {@link HandlerFunctions} can look it up without
 * WebSocket support on the classpath.
 */
public interface WebSocketHandlerFunction extends HandlerFunction<ServerResponse> {

	static boolean isWebSocketUpgrade(ServerRequest request) {
		String upgrade = request.headers().firstHeader(HttpHeaders.UPGRADE);
		return upgrade != null && "websocket".equals(upgrade.toLowerCase(Locale.ROOT));
	}

}
