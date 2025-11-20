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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import org.jspecify.annotations.Nullable;
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
					.append("name", config.getName())
					.append("regexp", config.getRegexp())
					.append("replacement", config.getReplacement())
					.toString();
			}
		};
	}

	protected void rewriteHeaders(ServerWebExchange exchange, Config config) {
		final String name = Objects.requireNonNull(config.getName(), "name must not be null");
		final HttpHeaders responseHeaders = exchange.getResponse().getHeaders();
		if (responseHeaders.get(name) != null) {
			List<String> oldValue = Objects.requireNonNull(responseHeaders.get(name), "oldValue must not be null");
			List<String> newValue = rewriteHeaders(config, oldValue);
			if (newValue != null) {
				responseHeaders.put(name, newValue);
			}
			else {
				responseHeaders.remove(name);
			}
		}
	}

	protected List<String> rewriteHeaders(Config config, List<String> headers) {
		String regexp = Objects.requireNonNull(config.getRegexp(), "regexp must not be null");
		String replacement = Objects.requireNonNull(config.getReplacement(), "replacement must not be null");
		ArrayList<String> rewrittenHeaders = new ArrayList<>();
		for (int i = 0; i < headers.size(); i++) {
			String rewriten = rewrite(headers.get(i), regexp, replacement);
			rewrittenHeaders.add(rewriten);
		}
		return rewrittenHeaders;
	}

	String rewrite(String value, String regexp, String replacement) {
		return value.replaceAll(regexp, replacement.replace("$\\", "$"));
	}

	public static class Config extends AbstractGatewayFilterFactory.NameConfig {

		private @Nullable String regexp;

		private @Nullable String replacement;

		public @Nullable String getRegexp() {
			return regexp;
		}

		public Config setRegexp(String regexp) {
			this.regexp = regexp;
			return this;
		}

		public @Nullable String getReplacement() {
			return replacement;
		}

		public Config setReplacement(String replacement) {
			this.replacement = replacement;
			return this;
		}

	}

}
