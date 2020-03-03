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

import reactor.core.publisher.Mono;

import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import org.springframework.util.unit.DataSize;
import org.springframework.web.server.ServerWebExchange;

import static org.springframework.cloud.gateway.support.GatewayToStringStyler.filterToStringCreator;

/**
 * This filter blocks the request, if the request size is more than the permissible size.
 * The default request size is 5 MB.
 *
 * @author Arpan
 */
public class RequestSizeGatewayFilterFactory extends
		AbstractGatewayFilterFactory<RequestSizeGatewayFilterFactory.RequestSizeConfig> {

	private static String PREFIX = "kMGTPE";

	private static String ERROR = "Request size is larger than permissible limit."
			+ " Request size is %s where permissible limit is %s";

	public RequestSizeGatewayFilterFactory() {
		super(RequestSizeGatewayFilterFactory.RequestSizeConfig.class);
	}

	private static String getErrorMessage(Long currentRequestSize, Long maxSize) {
		return String.format(ERROR, getReadableByteCount(currentRequestSize),
				getReadableByteCount(maxSize));
	}

	private static String getReadableByteCount(long bytes) {
		int unit = 1000;
		if (bytes < unit) {
			return bytes + " B";
		}
		int exp = (int) (Math.log(bytes) / Math.log(unit));
		String pre = Character.toString(PREFIX.charAt(exp - 1));
		return String.format("%.1f %sB", bytes / Math.pow(unit, exp), pre);
	}

	@Override
	public GatewayFilter apply(
			RequestSizeGatewayFilterFactory.RequestSizeConfig requestSizeConfig) {
		requestSizeConfig.validate();
		return new GatewayFilter() {
			@Override
			public Mono<Void> filter(ServerWebExchange exchange,
					GatewayFilterChain chain) {
				ServerHttpRequest request = exchange.getRequest();
				String contentLength = request.getHeaders().getFirst("content-length");
				if (!StringUtils.isEmpty(contentLength)) {
					Long currentRequestSize = Long.valueOf(contentLength);
					if (currentRequestSize > requestSizeConfig.getMaxSize().toBytes()) {
						exchange.getResponse()
								.setStatusCode(HttpStatus.PAYLOAD_TOO_LARGE);
						if (!exchange.getResponse().isCommitted()) {
							exchange.getResponse().getHeaders().add("errorMessage",
									getErrorMessage(currentRequestSize,
											requestSizeConfig.getMaxSize().toBytes()));
						}
						return exchange.getResponse().setComplete();
					}
				}
				return chain.filter(exchange);
			}

			@Override
			public String toString() {
				return filterToStringCreator(RequestSizeGatewayFilterFactory.this)
						.append("max", requestSizeConfig.getMaxSize()).toString();
			}
		};
	}

	public static class RequestSizeConfig {

		// TODO: use boot data size type
		private DataSize maxSize = DataSize.ofBytes(5000000L);

		public DataSize getMaxSize() {
			return maxSize;
		}

		public RequestSizeGatewayFilterFactory.RequestSizeConfig setMaxSize(
				DataSize maxSize) {
			this.maxSize = maxSize;
			return this;
		}

		// TODO: use validator annotation
		public void validate() {
			Assert.notNull(this.maxSize, "maxSize may not be null");
			Assert.isTrue(this.maxSize.toBytes() > 0, "maxSize must be greater than 0");
		}

	}

}
