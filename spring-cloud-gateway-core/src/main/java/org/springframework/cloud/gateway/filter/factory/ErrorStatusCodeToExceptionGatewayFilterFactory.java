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

package org.springframework.cloud.gateway.filter.factory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import reactor.core.publisher.Mono;

import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.OrderedGatewayFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;

import static org.springframework.cloud.gateway.support.GatewayToStringStyler.filterToStringCreator;
import static org.springframework.http.HttpStatus.Series.CLIENT_ERROR;
import static org.springframework.http.HttpStatus.Series.SERVER_ERROR;

/**
 * A filter that translates the error code from mesh implementations like Istio to a
 * forced @{@link ResponseStatusException} to gracefully honor
 * the @{@link SpringCloudCircuitBreakerFilterFactory} fallback logic. To be used in
 * conjunction with
 * @{@link SpringCloudCircuitBreakerFilterFactory} filter. You might need to
 * add @{@link DedupeResponseHeaderGatewayFilterFactory} as well to remove duplicate
 * response headers.
 * 
 * // @formatter:off
 * <p>
 * Usage: 
 *        To translate 4xx errors' to exception, 
 *         filters:
 *         - name: ErrorStatusCodeToException
 *           args:
 *             name: MeshErrorCodeToException
 *             responseStatusCodeSeries:
 *             - CLIENT_ERROR
 *
 * 		  To translate 4xx and 5xx errors' to exception, 
 *         filters:
 *         - name: ErrorStatusCodeToException
 *           args:
 *             name: MeshErrorCodeToException
 *             responseStatusCodeSeries:
 *             - CLIENT_ERROR
 *             - SERVER_ERROR
 * </p>
 * // @formatter:on
 * 
 * @author Srinivasa Vasu
 */
public class ErrorStatusCodeToExceptionGatewayFilterFactory extends
		AbstractGatewayFilterFactory<ErrorStatusCodeToExceptionGatewayFilterFactory.Config> {

	public ErrorStatusCodeToExceptionGatewayFilterFactory() {
		super(Config.class);
	}

	@Override
	public GatewayFilter apply(Config config) {

		return new OrderedGatewayFilter(new GatewayFilter() {
			@Override
			public Mono<Void> filter(ServerWebExchange exchange,
					GatewayFilterChain chain) {
				return chain.filter(exchange).then(Mono.defer(() -> {
					ServerHttpResponse response = exchange.getResponse();
					if (response.getStatusCode() != null
							&& config.getResponseStatusCodeSeries()
									.contains(response.getStatusCode().series())) {
						return Mono.error(
								new ResponseStatusException(response.getStatusCode(),
										response.getStatusCode().getReasonPhrase()));
					}
					return Mono.empty();
				}));
			}

			@Override
			public String toString() {
				return filterToStringCreator(
						ErrorStatusCodeToExceptionGatewayFilterFactory.this)
								.append("name", config.getName())
								.append("responseStatusCodeSeries",
										config.getResponseStatusCodeSeries())
								.toString();

			}
		}, this.getOrder());

	}

	@Override
	public List<String> shortcutFieldOrder() {
		return Collections.singletonList("responseStatusCodeSeries");
	}

	@Override
	public String name() {
		return "ErrorStatusCodeToException";
	}

	public int getOrder() {
		return Ordered.LOWEST_PRECEDENCE;
	}

	public static class Config extends AbstractGatewayFilterFactory.NameConfig {

		List<HttpStatus.Series> responseStatusCodeSeries = new ArrayList<HttpStatus.Series>() {
			{
				add(SERVER_ERROR);
				add(CLIENT_ERROR);
			}
		};

		public List<HttpStatus.Series> getResponseStatusCodeSeries() {
			return responseStatusCodeSeries;
		}

		public Config setResponseStatusCodeSeries(
				List<HttpStatus.Series> responseStatusCodeSeries) {
			this.responseStatusCodeSeries = responseStatusCodeSeries;
			return this;
		}

	}

}
