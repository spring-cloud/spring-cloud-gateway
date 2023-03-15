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

import io.micrometer.common.KeyValues;
import io.micrometer.common.lang.NonNull;
import io.micrometer.common.lang.Nullable;

import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.http.server.reactive.ServerHttpResponse;

import static org.springframework.cloud.gateway.filter.headers.observation.GatewayDocumentedObservation.HighCardinalityKeys.URI;
import static org.springframework.cloud.gateway.filter.headers.observation.GatewayDocumentedObservation.LowCardinalityKeys.METHOD;
import static org.springframework.cloud.gateway.filter.headers.observation.GatewayDocumentedObservation.LowCardinalityKeys.ROUTE_ID;
import static org.springframework.cloud.gateway.filter.headers.observation.GatewayDocumentedObservation.LowCardinalityKeys.ROUTE_URI;
import static org.springframework.cloud.gateway.filter.headers.observation.GatewayDocumentedObservation.LowCardinalityKeys.STATUS;

/**
 * Default implementation of the {@link GatewayObservationConvention}.
 *
 * @author Marcin Grzejszczak
 * @since 4.0.0
 */
public class DefaultGatewayObservationConvention implements GatewayObservationConvention {

	/**
	 * Default instance of the {@link DefaultGatewayObservationConvention}.
	 */
	public static final GatewayObservationConvention INSTANCE = new DefaultGatewayObservationConvention();

	@Override
	public KeyValues getLowCardinalityKeyValues(GatewayContext context) {
		KeyValues keyValues = KeyValues.empty();
		if (context.getCarrier() == null) {
			return keyValues;
		}
		Route route = context.getServerWebExchange().getAttribute(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR);
		keyValues = keyValues
				.and(ROUTE_URI.withValue(route.getUri().toString()),
						METHOD.withValue(context.getRequest().getMethod().name()))
				.and(ROUTE_ID.withValue(route.getId()));
		ServerHttpResponse response = context.getResponse();
		if (response != null && response.getStatusCode() != null) {
			keyValues = keyValues.and(STATUS.withValue(String.valueOf(response.getStatusCode().value())));
		}
		else {
			keyValues = keyValues.and(STATUS.withValue("UNKNOWN"));
		}
		return keyValues;
	}

	@Override
	public KeyValues getHighCardinalityKeyValues(GatewayContext context) {
		return KeyValues.of(URI.withValue(context.getRequest().getURI().toString()));
	}

	@Override
	@NonNull
	public String getName() {
		return "http.client.requests";
	}

	@Nullable
	@Override
	public String getContextualName(GatewayContext context) {
		if (context.getRequest() == null) {
			return null;
		}
		return "HTTP " + context.getRequest().getMethod();
	}

}
