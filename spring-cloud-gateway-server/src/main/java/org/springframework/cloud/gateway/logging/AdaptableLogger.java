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
package org.springframework.cloud.gateway.logging;

import org.springframework.web.server.ServerWebExchange;

/**
 * An abstraction over logging. For example, to maintain proper trace context when using
 * manual sleuth instrumentation the log call needs to be wrapped inside a special WebFlux
 * operator.
 */
public interface AdaptableLogger {

	void trace(ServerWebExchange exchange, Object message);

	void trace(ServerWebExchange exchange, Object message, Throwable throwable);

	void debug(ServerWebExchange exchange, Object message);

	void debug(ServerWebExchange exchange, Object message, Throwable throwable);

	void info(ServerWebExchange exchange, Object message);

	void info(ServerWebExchange exchange, Object message, Throwable throwable);

	void warn(ServerWebExchange exchange, Object message);

	void warn(ServerWebExchange exchange, Object message, Throwable throwable);

	void error(ServerWebExchange exchange, Object message);

	void error(ServerWebExchange exchange, Object message, Throwable t);

}
