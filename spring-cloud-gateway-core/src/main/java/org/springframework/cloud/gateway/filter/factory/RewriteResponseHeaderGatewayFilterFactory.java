/*
 * Copyright 2013-2018 the original author or authors.
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
 *
 */

package org.springframework.cloud.gateway.filter.factory;

import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.Arrays;
import java.util.List;

/**
 * @author Vitaliy Pavlyuk
 */
public class RewriteResponseHeaderGatewayFilterFactory extends AbstractGatewayFilterFactory<RewriteResponseHeaderGatewayFilterFactory.Config> {

	public static final String REGEXP_KEY = "regexp";
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
		return (exchange, chain) -> chain.filter(exchange).then(Mono.fromRunnable(() -> {
			rewriteHeader(exchange, config);
		}));
	}

	protected void rewriteHeader(ServerWebExchange exchange, Config config) {
		final String name = config.getName();
		final String value = exchange.getResponse().getHeaders().getFirst(name);
		if (value == null) {
			return;
		}
		final String newValue = rewrite(value, config.getRegexp(), config.getReplacement());
		exchange.getResponse().getHeaders().set(name, newValue);
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
