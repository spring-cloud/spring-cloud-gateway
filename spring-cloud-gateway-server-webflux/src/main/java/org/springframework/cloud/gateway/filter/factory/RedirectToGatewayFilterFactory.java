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
		HttpStatusHolder httpStatus = HttpStatusHolder.parse(status);
		Assert.isTrue(httpStatus.is3xxRedirection(), "status must be a 3xx code, but was " + status);
		return apply(httpStatus, URI.create(url), config);
	}

	public GatewayFilter apply(String statusString, String urlString) {
		return apply(statusString, urlString, false);
	}

	/**
	 * Retained for compatibility with the original include-request-parameters option.
	 */
	public GatewayFilter apply(String statusString, String urlString, boolean includeRequestParams) {
		HttpStatusHolder httpStatus = HttpStatusHolder.parse(statusString);
		Assert.isTrue(httpStatus.is3xxRedirection(), "status must be a 3xx code, but was " + statusString);
		Config config = new Config();
		config.setIncludeRequestParams(includeRequestParams);
		return apply(httpStatus, URI.create(urlString), config);
	}

	public GatewayFilter apply(HttpStatus httpStatus, URI uri) {
		return apply(new HttpStatusHolder(httpStatus, null), uri, new Config());
	}

	/**
	 * Retained for compatibility with the original include-request-parameters option.
	 */
	public GatewayFilter apply(HttpStatus httpStatus, URI uri, boolean includeRequestParams) {
		Config config = new Config();
		config.setIncludeRequestParams(includeRequestParams);
		return apply(new HttpStatusHolder(httpStatus, null), uri, config);
	}

	public GatewayFilter apply(HttpStatusHolder httpStatus, URI uri) {
		return apply(httpStatus, uri, new Config());
	}

	/**
	 * Retained for compatibility with the original include-request-parameters option.
	 */
	public GatewayFilter apply(HttpStatusHolder httpStatus, URI uri, boolean includeRequestParams) {
		Config config = new Config();
		config.setIncludeRequestParams(includeRequestParams);
		return apply(httpStatus, uri, config);
	}

	private GatewayFilter apply(HttpStatusHolder httpStatus, URI uri, Config config) {
		Assert.isTrue(httpStatus.is3xxRedirection(), "status must be a 3xx code, but was " + httpStatus);

		return new GatewayFilter() {
			@Override
			public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
				if (!exchange.getResponse().isCommitted()) {
					setResponseStatus(exchange, httpStatus);

					URI redirectUri = buildRedirectUri(uri, exchange, config);

					final ServerHttpResponse response = exchange.getResponse();
					response.getHeaders().set(HttpHeaders.LOCATION, redirectUri.toASCIIString());
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
					.append(INCLUDE_REQUEST_PARAMS_KEY, config.includeRequestParams)
					.append(INCLUDE_REQUEST_PATH_KEY, config.includeRequestPath)
					.toString();
			}
		};
	}

	private URI buildRedirectUri(URI uri, ServerWebExchange exchange, Config config) {
		StringBuilder redirectUri = new StringBuilder();

		if (uri.getScheme() != null) {
			redirectUri.append(uri.getScheme()).append(':');
		}

		if (uri.getRawAuthority() != null) {
			redirectUri.append("//").append(uri.getRawAuthority());
		}

		String destinationPath = uri.getRawPath() != null ? uri.getRawPath() : "";

		if (config.includeRequestPath) {
			String requestPath = exchange.getRequest().getURI().getRawPath();

			if (requestPath != null && !requestPath.isEmpty()) {
				if (destinationPath.endsWith("/") && requestPath.startsWith("/")) {
					requestPath = requestPath.substring(1);
				}
				destinationPath += requestPath;
			}
		}

		redirectUri.append(destinationPath);

		String destinationQuery = uri.getRawQuery();
		String requestQuery = config.includeRequestParams ? exchange.getRequest().getURI().getRawQuery() : null;

		if (destinationQuery != null && !destinationQuery.isEmpty()) {
			redirectUri.append('?').append(destinationQuery);
			if (requestQuery != null && !requestQuery.isEmpty()) {
				redirectUri.append('&').append(requestQuery);
			}
		}
		else if (requestQuery != null && !requestQuery.isEmpty()) {
			redirectUri.append('?').append(requestQuery);
		}

		if (uri.getRawFragment() != null) {
			redirectUri.append('#').append(uri.getRawFragment());
		}

		return URI.create(redirectUri.toString());
	}

	public static class Config {

		private @Nullable String status;

		private @Nullable String url;

		private boolean includeRequestParams;

		private boolean includeRequestPath;

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
