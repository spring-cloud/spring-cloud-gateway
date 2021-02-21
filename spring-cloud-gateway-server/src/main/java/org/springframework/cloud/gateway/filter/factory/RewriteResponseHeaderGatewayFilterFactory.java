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

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import reactor.core.publisher.Mono;

import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.web.server.ServerWebExchange;

import static org.springframework.cloud.gateway.support.GatewayToStringStyler.filterToStringCreator;

/**
 * @author Vitaliy Pavlyuk
 */
public class RewriteResponseHeaderGatewayFilterFactory
		extends AbstractGatewayFilterFactory<RewriteResponseHeaderGatewayFilterFactory.Config> {

	/**
	 * Regexp key.
	 */
	public static final String REGEXP_KEY = "regexp";

	/**
	 * Replacement key.
	 */
	public static final String REPLACEMENT_KEY = "replacement";

	public RewriteResponseHeaderGatewayFilterFactory() {
		super(Config.class);
	}

	@Override
	public List<String> shortcutFieldOrder() {
		return Arrays.asList(NAME_KEY, REGEXP_KEY, REPLACEMENT_KEY);
	}

	@Override
	public GatewayFilter apply(Config config) {
		return new GatewayFilter() {
			@Override
			public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
				return chain.filter(exchange).then(Mono.fromRunnable(() -> rewriteHeaders(exchange, config)));
			}

			@Override
			public String toString() {
				return filterToStringCreator(RewriteResponseHeaderGatewayFilterFactory.this)
						.append("name", config.getName()).append("regexp", config.getRegexp())
						.append("replacement", config.getReplacement()).toString();
			}
		};
	}

	@Deprecated
	protected void rewriteHeader(ServerWebExchange exchange, Config config) {
		rewriteHeaders(exchange, config);
	}

	protected void rewriteHeaders(ServerWebExchange exchange, Config config) {
		final String name = config.getName();
		final HttpHeaders responseHeaders = exchange.getResponse().getHeaders();
		responseHeaders.computeIfPresent(name, (k, v) -> rewriteHeaders(config, v));
	}

	protected List<String> rewriteHeaders(Config config, List<String> headers) {
		return headers.stream().map(val -> rewrite(val, config.getRegexp(), config.getReplacement()))
				.collect(Collectors.toList());
	}

	String rewrite(String value, String regexp, String replacement) {
		return value.replaceAll(regexp, replacement.replace("$\\", "$"));
	}

	public static class Config extends AbstractGatewayFilterFactory.NameConfig {

		private String regexp;

		private String replacement;

		public String getRegexp() {
			return regexp;
		}

		public Config setRegexp(String regexp) {
			this.regexp = regexp;
			return this;
		}

		public String getReplacement() {
			return replacement;
		}

		public Config setReplacement(String replacement) {
			this.replacement = replacement;
			return this;
		}

	}

}
