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

package org.springframework.cloud.gateway.handler.predicate;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import org.springframework.tuple.Tuple;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.util.patterns.PathPattern;
import org.springframework.web.util.patterns.PathPatternParser;

import static org.springframework.cloud.gateway.handler.support.RoutePredicateFactoryUtils.traceMatch;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.URI_TEMPLATE_VARIABLES_ATTRIBUTE;

/**
 * @author Spencer Gibb
 */
public class PathRoutePredicateFactory implements RoutePredicateFactory {

	private PathPatternParser pathPatternParser = new PathPatternParser();

	public void setPathPatternParser(PathPatternParser pathPatternParser) {
		this.pathPatternParser = pathPatternParser;
	}

	@Override
	public List<String> argNames() {
		return Collections.singletonList(PATTERN_KEY);
	}

	@Override
	public Predicate<ServerWebExchange> apply(Tuple args) {
		String unparsedPattern = args.getString(PATTERN_KEY);
		PathPattern pattern;
		synchronized (this.pathPatternParser) {
			pattern = this.pathPatternParser.parse(unparsedPattern);
		}

		return exchange -> {
			String path = exchange.getRequest().getURI().getPath();
			boolean match = pattern.matches(path);
			traceMatch("Pattern", pattern.getPatternString(), path, match);
			if (match) {
				Map<String, String> uriTemplateVariables = pattern.matchAndExtract(path);
				exchange.getAttributes().put(URI_TEMPLATE_VARIABLES_ATTRIBUTE, uriTemplateVariables);
				return true;
			}
			else {
				return false;
			}
		};
	}
}
