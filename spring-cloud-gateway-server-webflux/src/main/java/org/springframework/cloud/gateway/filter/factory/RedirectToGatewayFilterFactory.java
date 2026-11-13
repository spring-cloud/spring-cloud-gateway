/*
 * Copyright 2013-present the original author or authors.
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

import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import org.jspecify.annotations.Nullable;
import reactor.core.publisher.Mono;

import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.support.HttpStatusHolder;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.util.Assert;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.util.UriComponentsBuilder;

import static org.springframework.cloud.gateway.support.GatewayToStringStyler.filterToStringCreator;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.setResponseStatus;

/**
 * @author Spencer Gibb
 */
public class RedirectToGatewayFilterFactory
		extends AbstractGatewayFilterFactory<RedirectToGatewayFilterFactory.Config> {

	/**
	 * Status key.
	 */
	public static final String STATUS_KEY = "status";

	/**
	 * URL key.
	 */
	public static final String URL_KEY = "url";

	/**
	 * IncludeRequestParams key.
	 */
	public static final String INCLUDE_REQUEST_PARAMS_KEY = "includeRequestParams";

	/**
	 * IncludeRequestPath key.
	 */
	public static final String INCLUDE_REQUEST_PATH_KEY = "includeRequestPath";

	public RedirectToGatewayFilterFactory() {
		super(Config.class);
	}

	@Override
	public List<String> shortcutFieldOrder() {
		return Arrays.asList(STATUS_KEY, URL_KEY, INCLUDE_REQUEST_PARAMS_KEY, INCLUDE_REQUEST_PATH_KEY);
	}

	@Override
	public GatewayFilter apply(Config config) {
		String status = Objects.requireNonNull(config.status, "status must not be null");
		String url = Objects.requireNonNull(config.url, "url must not be null");
		return apply(status, url, config.includeRequestParams, config.includeRequestPath);
	}

	public GatewayFilter apply(String statusString, String urlString) {
		return apply(statusString, urlString, false, false);
	}

	public GatewayFilter apply(String statusString, String urlString, boolean includeRequestParams) {
		return apply(statusString, urlString, includeRequestParams, false);
	}

	public GatewayFilter apply(String statusString, String urlString, boolean includeRequestParams,
			boolean includeRequestPath) {
		HttpStatusHolder httpStatus = HttpStatusHolder.parse(statusString);
		Assert.isTrue(httpStatus.is3xxRedirection(), "status must be a 3xx code, but was " + statusString);
		final URI url = URI.create(urlString);
		return apply(httpStatus, url, includeRequestParams, includeRequestPath);
	}

	public GatewayFilter apply(HttpStatus httpStatus, URI uri) {
		return apply(new HttpStatusHolder(httpStatus, null), uri, false, false);
	}

	public GatewayFilter apply(HttpStatus httpStatus, URI uri, boolean includeRequestParams) {
		return apply(new HttpStatusHolder(httpStatus, null), uri, includeRequestParams, false);
	}

	public GatewayFilter apply(HttpStatus httpStatus, URI uri, boolean includeRequestParams,
			boolean includeRequestPath) {
		return apply(new HttpStatusHolder(httpStatus, null), uri, includeRequestParams, includeRequestPath);
	}

	public GatewayFilter apply(HttpStatusHolder httpStatus, URI uri) {
		return apply(httpStatus, uri, false, false);
	}

	public GatewayFilter apply(HttpStatusHolder httpStatus, URI uri, boolean includeRequestParams) {
		return apply(httpStatus, uri, includeRequestParams, false);
	}

	public GatewayFilter apply(HttpStatusHolder httpStatus, URI uri, boolean includeRequestParams,
			boolean includeRequestPath) {
		return new GatewayFilter() {
			@Override
			public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
				if (!exchange.getResponse().isCommitted()) {
					setResponseStatus(exchange, httpStatus);

					UriComponentsBuilder uriBuilder = UriComponentsBuilder.fromUri(uri);

					if (includeRequestPath) {
						String requestPath = exchange.getRequest().getURI().getPath();
						String destinationPath = uri.getRawPath();

						if (destinationPath != null && destinationPath.endsWith("/") && requestPath.startsWith("/")) {
							requestPath = requestPath.substring(1);
						}

						uriBuilder.path(requestPath);
					}

					if (includeRequestParams) {
						uriBuilder.queryParams(exchange.getRequest().getQueryParams());
					}

					String location = uriBuilder.build().toUri().toString();

					final ServerHttpResponse response = exchange.getResponse();
					response.getHeaders().set(HttpHeaders.LOCATION, location);
					return response.setComplete();
				}
				return Mono.empty();
			}

			@Override
			public String toString() {
				String status;
				if (httpStatus.getHttpStatus() != null) {
					status = String.valueOf(httpStatus.getHttpStatus().value());
				}
				else {
					status = httpStatus.getStatus().toString();
				}
				return filterToStringCreator(RedirectToGatewayFilterFactory.this).append(status, uri)
					.append(INCLUDE_REQUEST_PARAMS_KEY, includeRequestParams)
					.append(INCLUDE_REQUEST_PATH_KEY, includeRequestPath)
					.toString();
			}
		};
	}

	public static class Config {

		private @Nullable String status;

		private @Nullable String url;

		boolean includeRequestParams;

		boolean includeRequestPath;

		public @Nullable String getStatus() {
			return status;
		}

		public void setStatus(String status) {
			this.status = status;
		}

		public @Nullable String getUrl() {
			return url;
		}

		public void setUrl(String url) {
			this.url = url;
		}

		public boolean isIncludeRequestParams() {
			return includeRequestParams;
		}

		public void setIncludeRequestParams(boolean includeRequestParams) {
			this.includeRequestParams = includeRequestParams;
		}

		public boolean isIncludeRequestPath() {
			return includeRequestPath;
		}

		public void setIncludeRequestPath(boolean includeRequestPath) {
			this.includeRequestPath = includeRequestPath;
		}

	}

}
