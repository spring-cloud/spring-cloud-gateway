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

	void traceLog(Log log, ServerWebExchange exchange, Object message);

	void traceLog(Log log, ServerWebExchange exchange, Object message, Throwable throwable);

	void debugLog(Log log, ServerWebExchange exchange, Object message);

	void debugLog(Log log, ServerWebExchange exchange, Object message, Throwable throwable);

	void infoLog(Log log, ServerWebExchange exchange, Object message);

	void infoLog(Log log, ServerWebExchange exchange, Object message, Throwable throwable);

	void warnLog(Log log, ServerWebExchange exchange, Object message);

	void warnLog(Log log, ServerWebExchange exchange, Object message, Throwable throwable);

	void errorLog(Log log, ServerWebExchange exchange, Object message);

	void errorLog(Log log, ServerWebExchange exchange, Object message, Throwable t);

}
