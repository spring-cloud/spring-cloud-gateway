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

import java.util.Arrays;
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
 * A filter that translates the error code http response from mesh implementations like
 * Istio to a forced {@link org.springframework.web.server.ResponseStatusException} to
 * gracefully honor the {@link SpringCloudCircuitBreakerFilterFactory} fallback logic. To
 * be used in conjunction with {@link SpringCloudCircuitBreakerFilterFactory} filter.
 *
 * You might need to add {@link DedupeResponseHeaderGatewayFilterFactory} to remove
 * duplicate response headers after the fallback call or set clearResponseHeader to avoid
 * propagating response headers to the fallback proxy call.
 *
 * <pre>
 * Usage:
 * <u>To translate 4xx errors' to exception,</u>
 * {@code
 *  filters:
 *  - name: ErrorStatusCodeToException
 *    args:
 *      name: MeshErrorCodeToException
 *      responseStatusCodeSeries:
 *      - CLIENT_ERROR
 *      clearResponseHeader: true
 * }
 *
 * <u>To translate 4xx and 5xx errors' to exception,</u>
 * {@code
 *  filters:
 *  - name: ErrorStatusCodeToException
 *    args:
 *      name: MeshErrorCodeToException
 *      responseStatusCodeSeries:
 *      - CLIENT_ERROR
 *      - SERVER_ERROR
 * }
 *
 * <u>To use this with CircuitBreaker filter,</u>
 * {@code
 *  filters:
 *  - name: CircuitBreaker
 *    args:
 *      name: svc-fallback
 *      fallbackUri: forward:/fallback
 *  - name: ErrorStatusCodeToException
 *    args:
 *      name: MeshErrorCodeToException
 *      responseStatusCodeSeries:
 *      - CLIENT_ERROR
 *      - SERVER_ERROR
 *      clearResponseHeader: "true"
 * }
 * </pre>
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
						if (config.isClearResponseHeader()) {
							response.getHeaders().clear();
						}
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

		private List<HttpStatus.Series> responseStatusCodeSeries = Arrays
				.asList(SERVER_ERROR, CLIENT_ERROR);

		private boolean clearResponseHeader;

		public List<HttpStatus.Series> getResponseStatusCodeSeries() {
			return responseStatusCodeSeries;
		}

		public Config setResponseStatusCodeSeries(
				List<HttpStatus.Series> responseStatusCodeSeries) {
			this.responseStatusCodeSeries = responseStatusCodeSeries;
			return this;
		}

		public boolean isClearResponseHeader() {
			return clearResponseHeader;
		}

		public Config setClearResponseHeader(boolean clearResponseHeader) {
			this.clearResponseHeader = clearResponseHeader;
			return this;
		}

	}

}
