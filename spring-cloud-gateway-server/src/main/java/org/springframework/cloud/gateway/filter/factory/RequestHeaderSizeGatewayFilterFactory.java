/*
 * Copyright 2013-2020 the original author or authors.
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

import java.util.List;
import java.util.Map;

import reactor.core.publisher.Mono;

import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.util.unit.DataSize;
import org.springframework.util.unit.DataUnit;
import org.springframework.web.server.ServerWebExchange;

import static org.springframework.cloud.gateway.support.GatewayToStringStyler.filterToStringCreator;

/**
 * This filter validates the size of each Request Header in the request. If size of any of
 * the request header is greater than the configured maxSize,it blocks the request.
 * Default max size of request header is 16KB.
 *
 * @author Sakalya Deshpande
 */

public class RequestHeaderSizeGatewayFilterFactory
		extends AbstractGatewayFilterFactory<RequestHeaderSizeGatewayFilterFactory.Config> {

	private static String ERROR = "Request Header/s size is larger than permissible limit."
			+ " Request Header/s size is %s where permissible limit is %s";

	public RequestHeaderSizeGatewayFilterFactory() {
		super(RequestHeaderSizeGatewayFilterFactory.Config.class);
	}

	@Override
	public GatewayFilter apply(RequestHeaderSizeGatewayFilterFactory.Config config) {
		return new GatewayFilter() {
			@Override
			public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
				ServerHttpRequest request = exchange.getRequest();
				HttpHeaders headers = request.getHeaders();
				Long headerSizeInBytes = 0L;

				for (Map.Entry<String, List<String>> headerEntry : headers.entrySet()) {
					List<String> values = headerEntry.getValue();
					for (String value : values) {
						headerSizeInBytes += Long.valueOf(value.getBytes().length);
					}
				}

				if (headerSizeInBytes > config.getMaxSize().toBytes()) {
					exchange.getResponse().setStatusCode(HttpStatus.REQUEST_HEADER_FIELDS_TOO_LARGE);
					exchange.getResponse().getHeaders().add("errorMessage",
							getErrorMessage(headerSizeInBytes, config.getMaxSize()));
					return exchange.getResponse().setComplete();

				}

				return chain.filter(exchange);
			}

			@Override
			public String toString() {
				return filterToStringCreator(RequestHeaderSizeGatewayFilterFactory.this)
						.append("max", config.getMaxSize()).toString();
			}
		};
	}

	private static String getErrorMessage(Long currentRequestSize, DataSize maxSize) {
		return String.format(ERROR, DataSize.of(currentRequestSize, DataUnit.BYTES), maxSize);
	}

	public static class Config {

		private DataSize maxSize = DataSize.ofBytes(16000L);

		public DataSize getMaxSize() {
			return maxSize;
		}

		public void setMaxSize(DataSize maxSize) {
			this.maxSize = maxSize;
		}

	}

}
