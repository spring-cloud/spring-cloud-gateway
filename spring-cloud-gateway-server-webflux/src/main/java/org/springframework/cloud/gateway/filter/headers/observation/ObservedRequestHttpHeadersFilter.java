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

import io.micrometer.common.lang.Nullable;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import reactor.util.context.ContextView;

import org.springframework.cloud.gateway.filter.headers.HttpHeadersFilter;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.web.server.ServerWebExchange;

/**
 * Observation of {@link HttpHeadersFilter} for a request. It will start an
 * {@link Observation} when the requests are being filtered. The {@link Observation} will
 * be stopped when a response is being parsed via
 * {@link ObservedResponseHttpHeadersFilter}.
 *
 * @author Marcin Grzejszczak
 * @since 4.0.0
 */
public class ObservedRequestHttpHeadersFilter implements HttpHeadersFilter {

	private static final Log log = LogFactory.getLog(ObservedRequestHttpHeadersFilter.class);

	private final ObservationRegistry observationRegistry;

	@Nullable
	private final GatewayObservationConvention customGatewayObservationConvention;

	public ObservedRequestHttpHeadersFilter(ObservationRegistry observationRegistry) {
		this(observationRegistry, null);
	}

	public ObservedRequestHttpHeadersFilter(ObservationRegistry observationRegistry,
			@Nullable GatewayObservationConvention customGatewayObservationConvention) {
		this.observationRegistry = observationRegistry;
		this.customGatewayObservationConvention = customGatewayObservationConvention;
	}

	@Override
	public HttpHeaders filter(HttpHeaders input, ServerWebExchange exchange) {
		HttpHeaders newHeaders = new HttpHeaders();
		newHeaders.putAll(input);
		if (log.isDebugEnabled()) {
			log.debug("Will instrument the HTTP request headers " + newHeaders);
		}
		Observation parentObservation = getParentObservation(exchange);
		GatewayContext gatewayContext = new GatewayContext(newHeaders, exchange.getRequest(), exchange);
		Observation childObservation = GatewayDocumentedObservation.GATEWAY_HTTP_CLIENT_OBSERVATION.observation(
				this.customGatewayObservationConvention, DefaultGatewayObservationConvention.INSTANCE,
				() -> gatewayContext, this.observationRegistry);
		if (parentObservation != null) {
			childObservation.parentObservation(parentObservation);
		}
		childObservation.start();
		if (log.isDebugEnabled()) {
			log.debug("Client observation  " + childObservation + " created for the request. New headers are "
					+ newHeaders);
		}
		exchange.getAttributes().put(ServerWebExchangeUtils.GATEWAY_OBSERVATION_ATTR, childObservation);
		return newHeaders;
	}

	/**
	 * The "micrometer.observation" key comes from
	 * {@link io.micrometer.observation.contextpropagation.ObservationThreadLocalAccessor}
	 * that requires the Context Propagation library on the classpath. Since we don't know
	 * if it will be there on the classpath we're referencing the key via a String and
	 * then we're testing its presence in tests via a fixed test dependency to Context
	 * Propagation and {@code ObservationThreadLocalAccessor}.
	 * @param exchange server web exchange
	 * @return parent observation or {@code null} when there is none
	 */
	private Observation getParentObservation(ServerWebExchange exchange) {
		ContextView contextView = exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_REACTOR_CONTEXT_ATTR);
		if (contextView == null) {
			return null;
		}
		return contextView.getOrDefault("micrometer.observation", null);
	}

	@Override
	public boolean supports(Type type) {
		return type.equals(Type.REQUEST);
	}

}
