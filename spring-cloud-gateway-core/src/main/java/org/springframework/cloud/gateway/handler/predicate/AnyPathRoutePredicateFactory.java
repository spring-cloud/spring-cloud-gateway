/*
 * Copyright 2013-2018 the original author or authors.
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

package org.springframework.cloud.gateway.handler.predicate;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import javax.validation.constraints.NotEmpty;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.core.style.ToStringCreator;
import org.springframework.http.server.PathContainer;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPattern.PathMatchInfo;
import org.springframework.web.util.pattern.PathPatternParser;

import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.URI_TEMPLATE_VARIABLES_ATTRIBUTE;
import static org.springframework.cloud.gateway.support.ShortcutConfigurable.ShortcutType;
import static org.springframework.http.server.PathContainer.parsePath;

/**
 * @author Tao Qian
 */
public class AnyPathRoutePredicateFactory extends AbstractRoutePredicateFactory<AnyPathRoutePredicateFactory.Config> {
	private static final Log log = LogFactory.getLog(RoutePredicateFactory.class);

	private PathPatternParser pathPatternParser = new PathPatternParser();

	public AnyPathRoutePredicateFactory() {
		super(Config.class);
	}

	public void setPathPatternParser(PathPatternParser pathPatternParser) {
		this.pathPatternParser = pathPatternParser;
	}

	@Override
	public List<String> shortcutFieldOrder() {
		return Collections.singletonList("patterns");
	}

	@Override
	public ShortcutType shortcutType() {
		return ShortcutType.GATHER_LIST;
	}

	@Override
	public Predicate<ServerWebExchange> apply(Config config) {
		final List<PathPattern> pathPatterns;
		synchronized (this.pathPatternParser) {
			pathPatterns = config.getPatterns().stream().map(this.pathPatternParser::parse).collect(Collectors.toList());
		}
		return exchange -> {
			PathContainer path = parsePath(exchange.getRequest().getURI().getPath());

			for (PathPattern pathPattern : pathPatterns) {
				boolean match = pathPattern.matches(path);
				traceMatch("Pattern", pathPattern.getPatternString(), path, match);
				if (match) {
					PathMatchInfo uriTemplateVariables = pathPattern.matchAndExtract(path);
					exchange.getAttributes().put(URI_TEMPLATE_VARIABLES_ATTRIBUTE, uriTemplateVariables);
					return true;
				}
			}
			return false;
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
		@NotEmpty
		private List<String> patterns = new ArrayList<>();

		public List<String> getPatterns() {
			return patterns;
		}

		public Config setPatterns(List<String> patterns) {
			this.patterns = patterns;
			return this;
		}

		public Config setPatterns(String... patterns) {
		    this.patterns = Arrays.asList(patterns);
		    return this;
		}

		@Override
		public String toString() {
			return new ToStringCreator(this)
					.append("patterns", patterns)
					.toString();
		}
	}


}
