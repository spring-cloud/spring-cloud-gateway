/*
 * Copyright 2013-2018 the original author or authors.
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

/**
 * @author Tony Clarke
 */
public class GatewayMetricsFilter implements GlobalFilter, Ordered {

	private static final Log log = LogFactory.getLog(GatewayMetricsFilter.class);

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
		String httpStatusCodeStr = "NA";

		String httpMethod = exchange.getRequest().getMethodValue();

		// a non standard HTTPS status could be used. Let's be defensive here
		// it needs to be checked for first, otherwise the delegate response
		// who's status DIDN"T change, will be used
		if (exchange.getResponse() instanceof AbstractServerHttpResponse) {
			Integer statusInt = ((AbstractServerHttpResponse) exchange.getResponse())
					.getStatusCodeValue();
			if (statusInt != null) {
				status = String.valueOf(statusInt);
				httpStatusCodeStr = status;
				HttpStatus resolved = HttpStatus.resolve(statusInt);
				if (resolved != null) {
					// this is not a CUSTOM status, so use series here.
					outcome = resolved.series().name();
					status = resolved.name();
				}
			}
		}
		else {
			HttpStatus statusCode = exchange.getResponse().getStatusCode();
			if (statusCode != null) {
				httpStatusCodeStr = String.valueOf(statusCode.value());
				outcome = statusCode.series().name();
				status = statusCode.name();
			}
		}

		// TODO refactor to allow Tags provider like in MetricsWebFilter
		Route route = exchange.getAttribute(GATEWAY_ROUTE_ATTR);
		Tags tags = Tags.of("outcome", outcome, "status", status, "httpStatusCode",
				httpStatusCodeStr, "routeId", route.getId(), "routeUri",
				route.getUri().toString(), "httpMethod", httpMethod);

		if (log.isTraceEnabled()) {
			log.trace("gateway.requests tags: " + tags);
		}
		sample.stop(meterRegistry.timer("gateway.requests", tags));
	}

}
