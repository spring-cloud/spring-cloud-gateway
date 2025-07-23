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
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

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
		Observation childObservation = exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_OBSERVATION_ATTR);
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
		exchange.getAttributes().put(OBSERVATION_STOPPED, "true");
		return input;
	}

	@Override
	public boolean supports(Type type) {
		return type.equals(Type.RESPONSE);
	}

}
