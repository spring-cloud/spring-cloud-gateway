/*
 * Copyright 2013-2017 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package org.springframework.cloud.gateway.filter;

import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_REQUEST_URL_ATTR;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Objects;

import org.springframework.cloud.gateway.route.Route;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.AbstractServerHttpResponse;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.server.ServerWebExchange;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.Timer.Sample;
import reactor.core.publisher.Mono;

public class GatewayMetricsFilter implements GlobalFilter, Ordered {
	private MeterRegistry meterRegistry;

	public GatewayMetricsFilter(MeterRegistry meterRegistry) {
		this.meterRegistry = meterRegistry;
	}

	@Override
	public int getOrder() {
		// start the timer as soon as possible and report the metric event before we write
		// response to client
		return NettyWriteResponseFilter.WRITE_RESPONSE_FILTER_ORDER + 1;
	}

	@Override
	public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
		Sample sample = Timer.start(meterRegistry);

		return chain.filter(exchange).doOnSuccessOrError((aVoid, ex) -> {
			endTimerRespectingCommit(exchange, sample);
		});
	}

	private void endTimerRespectingCommit(ServerWebExchange exchange, Sample sample) {

		ServerHttpResponse response = exchange.getResponse();
		if (response.isCommitted()) {
			endTimerInner(exchange, sample);
		}
		else {
			response.beforeCommit(() -> {
				endTimerInner(exchange, sample);
				return Mono.empty();
			});
		}
	}

	private void endTimerInner(ServerWebExchange exchange, Sample sample) {
		String outcome = "CUSTOM";
		String status = "CUSTOM";
		HttpStatus statusCode = exchange.getResponse().getStatusCode();
		if (statusCode != null) {
			outcome = statusCode.series().name();
			status = statusCode.name();
		}
		else { // a non standard HTTPS status could be used. Let's be defensive here
			if (exchange.getResponse() instanceof AbstractServerHttpResponse) {
				Integer statusInt = ((AbstractServerHttpResponse) exchange.getResponse())
						.getStatusCodeValue();
				if (statusInt != null) {
					status = String.valueOf(statusInt);
				}
				else {
					status = "NA";
				}
			}
		}
		Route route = exchange.getAttribute(GATEWAY_ROUTE_ATTR);

		// Evaluate the URI for the metrics tag. Static URI from route
		// definition is used in case the request URI is null.
		String metricsUriString = Objects
				.nonNull(exchange.getAttribute(GATEWAY_REQUEST_URL_ATTR))
						? evaluateMetricsRequestUri(route.getUri().toString(),
								exchange.getAttribute(GATEWAY_REQUEST_URL_ATTR)
										.toString())
						: route.getUri().toString();
		Tags tags = Tags.of("outcome", outcome, "status", status, "routeId",
				route.getId(), "routeUri", metricsUriString);
		sample.stop(meterRegistry.timer("gateway.requests", tags));
	}

	/**
	 * Evaluates the URI to be used in the metrics tag. The URI path might contain
	 * variable segments representing IDs (like /user/1, /user/2 ...) which must not be
	 * considered for the metrics. So, if the request URI starts with the static URI from
	 * the route definition we can use this static route URI as its path segments can be
	 * considered as fixed. But if the request URI differs (e.g. by applying a forward
	 * filter) then we can only use the scheme, host and port from the request URI to
	 * avoid variable path segments.
	 *
	 * @param routeUri string representation of the route definition URI
	 * @param gatewayRequestUrl string representation of the request URL
	 * @return string representation of the URI to be used in the metrics tag
	 */
	private String evaluateMetricsRequestUri(String routeUri, String gatewayRequestUrl) {
		String uriSchemaHostPort;

		try {
			URI requestUri = new URI(gatewayRequestUrl);
			uriSchemaHostPort = requestUri.toString().startsWith(routeUri) ? routeUri
					: new URI(requestUri.getScheme(), null, requestUri.getHost(),
							requestUri.getPort(), null, null, null).toString();
		}
		catch (URISyntaxException e) {
			uriSchemaHostPort = routeUri;
		}

		return uriSchemaHostPort;
	}
}
