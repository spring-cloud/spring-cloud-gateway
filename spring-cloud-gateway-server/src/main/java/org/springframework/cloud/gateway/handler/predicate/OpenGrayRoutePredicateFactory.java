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


import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

import org.apache.commons.codec.digest.MurmurHash3;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.http.HttpCookie;
import org.springframework.util.ObjectUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.server.ServerWebExchange;

/**
 * other than WeightRoutePredicateFactory route traffic random,
 * this predicate route traffic base on specific parameter
 */

public class OpenGrayRoutePredicateFactory extends AbstractRoutePredicateFactory<OpenGrayRoutePredicateFactory.Config> {
	private static final Log log = LogFactory.getLog(OpenGrayRoutePredicateFactory.class);
	private final String REQUEST_QUERY_PATTERN = "request.query.";
	private final String REQUEST_HEADER_PATTERN = "request.header.";
	private final String REQUEST_COOKIE_PATTERN = "request.cookie.";
	private final String REQUEST_PATH_PATTERN = "request.path";

	/**
	 * available range is [0, 1000)
	 */
	public static final int MAX_WEIGHT_RANGE = 1000;

	public static final String FILED_KEY = "fieldPattern";
	/**
	 *
	 */
	public static final String START_KEY = "start";
	public static final String END_KEY = "end";

	public static final String MATCH_EMPTY_FIELD_KEY = "matchEmptyField";


	public OpenGrayRoutePredicateFactory() {
		super(Config.class);
	}

	@Override
	public List<String> shortcutFieldOrder() {
		return Arrays.asList(FILED_KEY, START_KEY, END_KEY, MATCH_EMPTY_FIELD_KEY);
	}

	@Override
	public Predicate<ServerWebExchange> apply(Config config) {
		return new GatewayPredicate() {
			@Override
			public boolean test(ServerWebExchange exchange) {
				// get gray value
				String patternValue = patternParamResolve(exchange, config.getFieldPattern());
				if (ObjectUtils.isEmpty(patternValue)) {
					return config.isMatchEmptyField();
				}


				int weightStart = Math.max(0, config.getStart());
				int weightEnd = Math.min(MAX_WEIGHT_RANGE, config.getEnd());

				if (weightEnd < weightStart) {
					// invalid match range
					return false;
				}

				int val = MurmurHash3.hash32x86(patternValue.getBytes(StandardCharsets.UTF_8));

				int hash = Math.abs(val == Integer.MIN_VALUE ? val++ : val) % MAX_WEIGHT_RANGE;

				return hash >= weightStart && hash < weightEnd;
			}

			@Override
			public Object getConfig() {
				return config;
			}

			@Override
			public String toString() {
				return String.format("field: %s ,start=%s ,end=%s", config.getFieldPattern(), config.getStart(), config.getEnd());
			}
		};
	}

	private String patternParamResolve(ServerWebExchange exchange, String singlePattern) {
		if (ObjectUtils.isEmpty(singlePattern)) {
			return "";
		}
		if (singlePattern.startsWith(REQUEST_QUERY_PATTERN)) {
			String fieldName = singlePattern.substring(REQUEST_QUERY_PATTERN.length());
			return Optional.ofNullable(exchange.getRequest().getQueryParams().getFirst(fieldName)).orElse("");
		} else if (singlePattern.startsWith(REQUEST_HEADER_PATTERN)) {
			String headerName = singlePattern.substring(REQUEST_HEADER_PATTERN.length());
			return Optional.ofNullable(exchange.getRequest().getHeaders().getFirst(headerName)).orElse("");
		} else if (singlePattern.startsWith(REQUEST_COOKIE_PATTERN)) {
			String cookieName = singlePattern.substring(REQUEST_COOKIE_PATTERN.length());
			return Optional.ofNullable(exchange.getRequest().getCookies().getFirst(cookieName))
					.map(HttpCookie::getValue)
					.orElse("");
		} else if (Objects.equals(singlePattern, REQUEST_PATH_PATTERN)) {
			return Optional.ofNullable(exchange.getRequest().getURI().getPath()).orElse("");
		} else {
			// unsupported
			return "";
		}
	}

	/**
	 * match range [start, end) range. [0, 1000) full match range
	 */
	@Validated
	public static class Config {

		/**
		 * field pattern to specify which field weight calculate base on
		 * <p>
		 * request.header.xx
		 * request.query.xx
		 * request.cookie.xx
		 * request.path
		 * <p/>
		 */

		private String fieldPattern;

		/**
		 * how to handle empty field value. false miss-match, true always match
		 */
		private boolean matchEmptyField = false;

		/**
		 * weight range include start, valid input [0, 999]
		 */
		private int start;
		/**
		 * weight range exclude end, valid input [1, 1000]
		 */
		private int end;

		public String getFieldPattern() {
			return fieldPattern;
		}

		public void setFieldPattern(String fieldPattern) {
			this.fieldPattern = fieldPattern;
		}

		public int getStart() {
			return start;
		}

		public void setStart(int start) {
			this.start = start;
		}

		public int getEnd() {
			return end;
		}

		public void setEnd(int end) {
			this.end = end;
		}

		public boolean isMatchEmptyField() {
			return matchEmptyField;
		}

		public void setMatchEmptyField(boolean matchEmptyField) {
			this.matchEmptyField = matchEmptyField;
		}
	}

}
