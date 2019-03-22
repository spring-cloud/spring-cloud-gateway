/*
 * Copyright 2013-2019 the original author or authors.
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

package org.springframework.cloud.gateway.filter;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.Timer.Sample;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import reactor.core.publisher.Mono;

import org.springframework.cloud.gateway.route.Route;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.AbstractServerHttpResponse;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.server.ServerWebExchange;

import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR;

public class GatewayMetricsFilter implements GlobalFilter, Ordered {

	private final Log log = LogFactory.getLog(getClass());

	private final MeterRegistry meterRegistry;

	public GatewayMetricsFilter(MeterRegistry meterRegistry) {
		this.meterRegistry = meterRegistry;
	}

	@Override
	public int getOrder() {
		// start the timer as soon as possible and report the metric event before we write
		// response to client
		return Ordered.HIGHEST_PRECEDENCE + 10000;
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
		Tags tags = Tags.of("outcome", outcome, "status", status, "routeId",
				route.getId(), "routeUri", route.getUri().toString());
		if (log.isTraceEnabled()) {
			log.trace("Stopping timer 'gateway.requests' with tags " + tags);
		}
		sample.stop(meterRegistry.timer("gateway.requests", tags));
	}

}
