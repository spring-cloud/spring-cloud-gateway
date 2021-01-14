package org.springframework.cloud.gateway.logging;

import org.apache.commons.logging.Log;
import org.springframework.web.server.ServerWebExchange;

/**
 * An abstraction over logging to all alternative implementations. For example, to
 * maintain proper trace context when using manual sleuth instrumentation the log call
 * needs to be wrapped inside a special WebFlux operator. Presently, it assumes apache
 * commons logging as the logging interface (dominant in Spring)
 */
public interface AdaptableLogger {

	void trace(Log log, ServerWebExchange exchange, Object message);

	void trace(Log log, ServerWebExchange exchange, Object message, Throwable throwable);

	void debug(Log log, ServerWebExchange exchange, Object message);

	void debug(Log log, ServerWebExchange exchange, Object message, Throwable throwable);

	void info(Log log, ServerWebExchange exchange, Object message);

	void info(Log log, ServerWebExchange exchange, Object message, Throwable throwable);

	void warn(Log log, ServerWebExchange exchange, Object message);

	void warn(Log log, ServerWebExchange exchange, Object message, Throwable throwable);

	void error(Log log, ServerWebExchange exchange, Object message);

	void error(Log log, ServerWebExchange exchange, Object message, Throwable t);

}
