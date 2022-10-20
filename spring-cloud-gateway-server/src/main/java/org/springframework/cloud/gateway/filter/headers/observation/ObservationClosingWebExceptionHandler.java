/*
 * Copyright 2013-2022 the original author or authors.
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

package org.springframework.cloud.gateway.filter.headers.observation;

import io.micrometer.observation.Observation;
import reactor.core.publisher.Mono;

import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.core.log.LogAccessor;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebExceptionHandler;

/**
 * A {@link WebExceptionHandler} that will stop an {@link Observation} if an exception was
 * thrown and there is no response to set on the context.
 *
 * @author Marcin Grzejszczak
 * @since 4.0.0
 */
public class ObservationClosingWebExceptionHandler implements WebExceptionHandler {

	private static final LogAccessor log = new LogAccessor(ObservationClosingWebExceptionHandler.class);

	@Override
	public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
		Object attribute = exchange.getAttribute(ObservedResponseHttpHeadersFilter.OBSERVATION_STOPPED);
		if (attribute == null) {
			Observation observation = exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_OBSERVATION_ATTR);
			if (observation != null) {
				if (log.isDebugEnabled()) {
					observation.scoped(() -> log.debug(
							() -> "An exception occurred and observation was not previously stopped, will stop it. The exception was ["
									+ ex + "]"));
				}
				observation.error(ex);
				observation.stop();
			}
		}
		return Mono.error(ex);
	}

}
