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

package org.springframework.cloud.gateway.filter.headers.observation;

import io.micrometer.observation.Observation;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jspecify.annotations.Nullable;

import org.springframework.cloud.gateway.filter.headers.HttpHeadersFilter;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.web.server.ServerWebExchange;

/**
 * Observation representation of {@link HttpHeadersFilter} for a response. It is started
 * in {@link ObservedRequestHttpHeadersFilter} and it's stopped in this class.
 *
 * @author Marcin Grzejszczak
 * @since 4.0.0
 */
public class ObservedResponseHttpHeadersFilter implements HttpHeadersFilter {

	private static final Log log = LogFactory.getLog(ObservedResponseHttpHeadersFilter.class);

	static final String OBSERVATION_STOPPED = "gateway.observation.stopped";

	@Override
	public HttpHeaders filter(HttpHeaders input, ServerWebExchange exchange) {
		Observation childObservation = claimObservation(exchange);
		if (childObservation == null) {
			return input;
		}
		Observation.Context childObservationContext = childObservation.getContext();
		if (childObservationContext instanceof GatewayContext context) {
			if (log.isDebugEnabled()) {
				log.debug("Will instrument the response");
			}
			context.setResponse(exchange.getResponse());
			if (log.isDebugEnabled()) {
				log.debug("The response was handled for observation " + childObservation);
			}
		}
		childObservation.stop();
		return input;
	}

	/**
	 * Takes the current client observation off the exchange, so that exactly one caller
	 * stops it - no matter whether the response, an error, the end of the exchange or a
	 * retry comes first.
	 * @param exchange the current exchange
	 * @return the observation the caller has to stop, or {@code null} if there is none or
	 * another caller already took it
	 */
	static @Nullable Observation claimObservation(ServerWebExchange exchange) {
		Object attribute = exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_OBSERVATION_ATTR);
		if (attribute instanceof Observation observation
				&& exchange.getAttributes().remove(ServerWebExchangeUtils.GATEWAY_OBSERVATION_ATTR, observation)) {
			exchange.getAttributes().put(OBSERVATION_STOPPED, "true");
			return observation;
		}
		return null;
	}

	@Override
	public boolean supports(Type type) {
		return type.equals(Type.RESPONSE);
	}

}
