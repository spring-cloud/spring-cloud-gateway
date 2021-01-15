package org.springframework.cloud.gateway.logging;

import org.springframework.web.server.ServerWebExchange;

/**
 * An abstraction over logging. For example, to maintain proper trace context when using manual sleuth
 * instrumentation the log call needs to be wrapped inside a special WebFlux operator.
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
