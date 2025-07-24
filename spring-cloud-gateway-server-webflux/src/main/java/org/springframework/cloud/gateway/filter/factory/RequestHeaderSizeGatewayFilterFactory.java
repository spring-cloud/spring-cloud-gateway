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

import java.util.Collections;
import java.util.HashMap;
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
 * This filter validates the size of each Request Header in the request, including the
 * key. If size of the request header is greater than the configured maxSize, it blocks
 * the request. Default max size of request header is 16KB.
 *
 * @author Sakalya Deshpande
 * @author Marta Medio
 */

public class RequestHeaderSizeGatewayFilterFactory
		extends AbstractGatewayFilterFactory<RequestHeaderSizeGatewayFilterFactory.Config> {

	private static String ERROR_PREFIX = "Request Header/s size is larger than permissible limit (%s).";

	private static String ERROR = " Request Header/s size for '%s' is %s.";

	public RequestHeaderSizeGatewayFilterFactory() {
		super(RequestHeaderSizeGatewayFilterFactory.Config.class);
	}

	@Override
	public List<String> shortcutFieldOrder() {
		return Collections.singletonList("maxSize");
	}

	@Override
	public GatewayFilter apply(RequestHeaderSizeGatewayFilterFactory.Config config) {
		String errorHeaderName = config.getErrorHeaderName() != null ? config.getErrorHeaderName() : "errorMessage";
		return new GatewayFilter() {
			@Override
			public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
				ServerHttpRequest request = exchange.getRequest();
				HttpHeaders headers = request.getHeaders();
				HashMap<String, Long> longHeaders = new HashMap<>();

				for (Map.Entry<String, List<String>> headerEntry : headers.headerSet()) {
					long headerSizeInBytes = 0L;
					headerSizeInBytes += headerEntry.getKey().getBytes().length;
					List<String> values = headerEntry.getValue();
					for (String value : values) {
						headerSizeInBytes += value.getBytes().length;
					}
					if (headerSizeInBytes > config.getMaxSize().toBytes()) {
						longHeaders.put(headerEntry.getKey(), headerSizeInBytes);
					}
				}

				if (!longHeaders.isEmpty()) {
					exchange.getResponse().setStatusCode(HttpStatus.REQUEST_HEADER_FIELDS_TOO_LARGE);
					exchange.getResponse()
						.getHeaders()
						.add(errorHeaderName, getErrorMessage(longHeaders, config.getMaxSize()));
					return exchange.getResponse().setComplete();

				}

				return chain.filter(exchange);
			}

			@Override
			public String toString() {
				return filterToStringCreator(RequestHeaderSizeGatewayFilterFactory.this)
					.append("maxSize", config.getMaxSize())
					.toString();
			}
		};
	}

	private static String getErrorMessage(HashMap<String, Long> longHeaders, DataSize maxSize) {
		StringBuilder msg = new StringBuilder(String.format(ERROR_PREFIX, maxSize));
		longHeaders
			.forEach((header, size) -> msg.append(String.format(ERROR, header, DataSize.of(size, DataUnit.BYTES))));
		return msg.toString();
	}

	public static class Config {

		private DataSize maxSize = DataSize.ofBytes(16000L);

		private String errorHeaderName;

		public DataSize getMaxSize() {
			return maxSize;
		}

		public void setMaxSize(DataSize maxSize) {
			this.maxSize = maxSize;
		}

		public String getErrorHeaderName() {
			return errorHeaderName;
		}

		public void setErrorHeaderName(String errorHeaderName) {
			this.errorHeaderName = errorHeaderName;
		}

	}

}
