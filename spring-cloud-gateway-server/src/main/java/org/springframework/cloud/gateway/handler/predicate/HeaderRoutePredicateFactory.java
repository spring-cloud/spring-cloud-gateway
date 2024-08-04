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

package org.springframework.cloud.gateway.handler.predicate;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;
import java.util.regex.Pattern;

import jakarta.validation.constraints.NotEmpty;

import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.server.ServerWebExchange;

/**
 * @author Spencer Gibb
 */
public class HeaderRoutePredicateFactory extends AbstractRoutePredicateFactory<HeaderRoutePredicateFactory.Config> {

	/**
	 * Header key.
	 */
	public static final String HEADER_KEY = "header";

	/**
	 * Regexp key.
	 */
	public static final String REGEXP_KEY = "regexp";

	public HeaderRoutePredicateFactory() {
		super(Config.class);
	}

	@Override
	public List<String> shortcutFieldOrder() {
		return Arrays.asList(HEADER_KEY, REGEXP_KEY);
	}

	@Override
	public Predicate<ServerWebExchange> apply(Config config) {
		Pattern pattern = (StringUtils.hasText(config.regexp)) ? Pattern.compile(config.regexp) : null;

		return new GatewayPredicate() {
			@Override
			public boolean test(ServerWebExchange exchange) {
				List<String> values = exchange.getRequest()
					.getHeaders()
					.getOrDefault(config.header, Collections.emptyList());
				if (values.isEmpty()) {
					return false;
				}
				// values is now guaranteed to not be empty
				if (pattern != null) {
					// check if a header value matches
					for (int i = 0; i < values.size(); i++) {
						String value = values.get(i);
						if (pattern.asMatchPredicate().test(value)) {
							return true;
						}
					}
					return false;
				}

				// there is a value and since regexp is empty, we only check existence.
				return true;
			}

			@Override
			public Object getConfig() {
				return config;
			}

			@Override
			public String toString() {
				return String.format("Header: %s regexp=%s", config.header, config.regexp);
			}
		};
	}

	@Validated
	public static class Config {

		@NotEmpty
		private String header;

		private String regexp;

		public String getHeader() {
			return header;
		}

		public Config setHeader(String header) {
			this.header = header;
			return this;
		}

		public String getRegexp() {
			return regexp;
		}

		public Config setRegexp(String regexp) {
			this.regexp = regexp;
			return this;
		}

	}

}
