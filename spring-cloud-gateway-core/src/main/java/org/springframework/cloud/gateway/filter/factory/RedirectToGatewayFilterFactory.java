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

package org.springframework.cloud.gateway.filter.factory;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.Arrays;
import java.util.List;

import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.util.Assert;

import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.parse;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.setResponseStatus;

import reactor.core.publisher.Mono;

/**
 * @author Spencer Gibb
 */
public class RedirectToGatewayFilterFactory extends AbstractGatewayFilterFactory<RedirectToGatewayFilterFactory.Config> {

	public static final String STATUS_KEY = "status";
	public static final String URL_KEY = "url";

	public RedirectToGatewayFilterFactory() {
		super(Config.class);
	}

	@Override
	public List<String> shortcutFieldOrder() {
		return Arrays.asList(STATUS_KEY, URL_KEY);
	}

	@Override
	public GatewayFilter apply(Config config) {
		return apply(config.status, config.url);
	}

	public GatewayFilter apply(String statusString, String urlString) {
		final HttpStatus httpStatus = parse(statusString);
		Assert.isTrue(httpStatus.is3xxRedirection(), "status must be a 3xx code, but was " + statusString);
		final URL url;
		try {
			url = URI.create(urlString).toURL();
		} catch (MalformedURLException e) {
			throw new IllegalArgumentException("Invalid url " + urlString, e);
		}
		return apply(httpStatus, url);
	}

	public GatewayFilter apply(HttpStatus httpStatus, URL url) {

		return (exchange, chain) ->
			chain.filter(exchange).then(Mono.defer(() -> {
				if (!exchange.getResponse().isCommitted()) {
					setResponseStatus(exchange, httpStatus);

					final ServerHttpResponse response = exchange.getResponse();
					response.getHeaders().set(HttpHeaders.LOCATION, url.toString());
					return response.setComplete();
				}
				return Mono.empty();
			}));
	}

	public static class Config {
		String status;
		String url;

		public String getStatus() {
			return status;
		}

		public void setStatus(String status) {
			this.status = status;
		}

		public String getUrl() {
			return url;
		}

		public void setUrl(String url) {
			this.url = url;
		}
	}

}
