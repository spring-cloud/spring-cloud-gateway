package org.springframework.cloud.gateway.logging;

import org.apache.commons.logging.Log;
import org.springframework.web.server.ServerWebExchange;

public class PassthroughLogger implements AdaptableLogger {

	@Override
	public void trace(Log log, ServerWebExchange exchange, Object message) {
		if (log.isTraceEnabled()) {
			log.trace(message);
		}
	}

	@Override
	public void trace(Log log, ServerWebExchange exchange, Object message, Throwable throwable) {
		if (log.isTraceEnabled()) {
			log.trace(message, throwable);
		}
	}

	@Override
	public void debug(Log log, ServerWebExchange exchange, Object message) {
		if (log.isDebugEnabled()) {
			log.debug(message);
		}

	}

	@Override
	public void debug(Log log, ServerWebExchange exchange, Object message, Throwable throwable) {
		if (log.isDebugEnabled()) {
			log.debug(message, throwable);
		}

	}

	@Override
	public void info(Log log, ServerWebExchange exchange, Object message) {
		if (log.isInfoEnabled()) {
			log.info(message);
		}
	}

	@Override
	public void info(Log log, ServerWebExchange exchange, Object message, Throwable throwable) {
		if (log.isInfoEnabled()) {
			log.info(message, throwable);
		}
	}

	@Override
	public void warn(Log log, ServerWebExchange exchange, Object message) {
		if (log.isWarnEnabled()) {
			log.warn(message);
		}
	}

	@Override
	public void warn(Log log, ServerWebExchange exchange, Object message, Throwable throwable) {
		if (log.isWarnEnabled()) {
			log.warn(message, throwable);
		}
	}

	@Override
	public void error(Log log, ServerWebExchange exchange, Object message) {
		if (log.isErrorEnabled()) {
			log.error(message);
		}
	}

	@Override
	public void error(Log log, ServerWebExchange exchange, Object message, Throwable t) {
		if (log.isErrorEnabled()) {
			log.error(message, t);
		}
	}

}
