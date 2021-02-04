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

import org.apache.commons.logging.Log;
import org.springframework.web.server.ServerWebExchange;

public class PassthroughLogger implements AdaptableLogger {

	private Log log;

	public PassthroughLogger(Log log) {
		this.log = log;
	}

	@Override
	public void trace(ServerWebExchange exchange, Object message) {
		if (log.isTraceEnabled()) {
			log.trace(message);
		}
	}

	@Override
	public void trace(ServerWebExchange exchange, Object message, Throwable throwable) {
		if (log.isTraceEnabled()) {
			log.trace(message, throwable);
		}
	}

	@Override
	public void debug(ServerWebExchange exchange, Object message) {
		if (log.isDebugEnabled()) {
			log.debug(message);
		}

	}

	@Override
	public void debug(ServerWebExchange exchange, Object message, Throwable throwable) {
		if (log.isDebugEnabled()) {
			log.debug(message, throwable);
		}

	}

	@Override
	public void info(ServerWebExchange exchange, Object message) {
		if (log.isInfoEnabled()) {
			log.info(message);
		}
	}

	@Override
	public void info(ServerWebExchange exchange, Object message, Throwable throwable) {
		if (log.isInfoEnabled()) {
			log.info(message, throwable);
		}
	}

	@Override
	public void warn(ServerWebExchange exchange, Object message) {
		if (log.isWarnEnabled()) {
			log.warn(message);
		}
	}

	@Override
	public void warn(ServerWebExchange exchange, Object message, Throwable throwable) {
		if (log.isWarnEnabled()) {
			log.warn(message, throwable);
		}
	}

	@Override
	public void error(ServerWebExchange exchange, Object message) {
		if (log.isErrorEnabled()) {
			log.error(message);
		}
	}

	@Override
	public void error(ServerWebExchange exchange, Object message, Throwable t) {
		if (log.isErrorEnabled()) {
			log.error(message, t);
		}
	}

}
