/*
 * Copyright 2013-2017 the original author or authors.
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

package org.springframework.cloud.gateway.handler.predicate;

import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.core.style.ToStringCreator;
import org.springframework.http.server.PathContainer;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPattern.PathMatchInfo;
import org.springframework.web.util.pattern.PathPatternParser;

import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.putUriTemplateVariables;
import static org.springframework.http.server.PathContainer.parsePath;

/**
 * @author Spencer Gibb
 */
public class PathRoutePredicateFactory extends AbstractRoutePredicateFactory<PathRoutePredicateFactory.Config> {
	private static final Log log = LogFactory.getLog(RoutePredicateFactory.class);
	private static final String MATCH_OPTIONAL_TRAILING_SEPARATOR_KEY = "matchOptionalTrailingSeparator";

	private PathPatternParser pathPatternParser = new PathPatternParser();

	public PathRoutePredicateFactory() {
		super(Config.class);
	}

	public void setPathPatternParser(PathPatternParser pathPatternParser) {
		this.pathPatternParser = pathPatternParser;
	}

	@Override
	public List<String> shortcutFieldOrder() {
		return Arrays.asList(PATTERN_KEY, MATCH_OPTIONAL_TRAILING_SEPARATOR_KEY);
	}

	@Override
	public Predicate<ServerWebExchange> apply(Config config) {
		synchronized (this.pathPatternParser) {
			pathPatternParser.setMatchOptionalTrailingSeparator(config.isMatchOptionalTrailingSeparator());
			config.pathPattern = this.pathPatternParser.parse(config.pattern);
		}
		return exchange -> {
			PathContainer path = parsePath(exchange.getRequest().getURI().getRawPath());

			boolean match = config.pathPattern.matches(path);
			traceMatch("Pattern", config.pathPattern.getPatternString(), path, match);
			if (match) {
				PathMatchInfo pathMatchInfo = config.pathPattern.matchAndExtract(path);
				putUriTemplateVariables(exchange, pathMatchInfo.getUriVariables());
			}
			return match;
		};
	}

	private static void traceMatch(String prefix, Object desired, Object actual, boolean match) {
		if (log.isTraceEnabled()) {
			String message = String.format("%s \"%s\" %s against value \"%s\"",
					prefix, desired, match ? "matches" : "does not match", actual);
			log.trace(message);
		}
	}

	@Validated
	public static class Config {
		private String pattern;
		private PathPattern pathPattern;
		private boolean matchOptionalTrailingSeparator = true;

		public String getPattern() {
			return pattern;
		}

		public Config setPattern(String pattern) {
			this.pattern = pattern;
			return this;
		}

		public boolean isMatchOptionalTrailingSeparator() {
			return matchOptionalTrailingSeparator;
		}

		public Config setMatchOptionalTrailingSeparator(boolean matchOptionalTrailingSeparator) {
			this.matchOptionalTrailingSeparator = matchOptionalTrailingSeparator;
			return this;
		}

		@Override
		public String toString() {
			return new ToStringCreator(this)
					.append("pattern", pattern)
					.append("matchOptionalTrailingSeparator", matchOptionalTrailingSeparator)
					.toString();
		}
	}


}
