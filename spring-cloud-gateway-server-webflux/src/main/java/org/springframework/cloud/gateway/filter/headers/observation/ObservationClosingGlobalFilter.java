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
import reactor.core.publisher.Mono;
import reactor.core.publisher.SignalType;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.log.LogAccessor;
import org.springframework.web.server.ServerWebExchange;

/**
 * Stops the client observation started in {@link ObservedRequestHttpHeadersFilter} when
 * the exchange ends before {@link ObservedResponseHttpHeadersFilter} or
 * {@link ObservationClosingWebExceptionHandler} could stop it - e.g. because the client
 * disconnected while the gateway was still waiting for the response headers of the
 * proxied service, or because a websocket session ended, which never runs the response
 * headers filters. Without this the observation would never be stopped and, for example,
 * {@code http.client.requests.active} would keep growing.
 *
 * @author Kai Gülzau
 * @see ObservationClosingWebExceptionHandler
 */
public class ObservationClosingGlobalFilter implements GlobalFilter, Ordered {

	private static final LogAccessor log = new LogAccessor(ObservationClosingGlobalFilter.class);

	@Override
	public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
		return chain.filter(exchange).doFinally(signalType -> stopObservation(exchange, signalType));
	}

	private void stopObservation(ServerWebExchange exchange, SignalType signalType) {
		Observation observation = ObservedResponseHttpHeadersFilter.claimObservation(exchange);
		if (observation != null) {
			log.debug(() -> "The exchange ended with " + signalType
					+ " and observation was not previously stopped, will stop it");
			observation.stop();
		}
	}

	@Override
	public int getOrder() {
		return Ordered.HIGHEST_PRECEDENCE;
	}

}
