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

import java.util.Map;
import java.util.function.Predicate;

import org.springframework.util.PathMatcher;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.support.HttpRequestPathHelper;
import org.springframework.web.util.ParsingPathMatcher;

/**
 * @author Spencer Gibb
 */
public class PathRoutePredicate implements RoutePredicate {

	public static final String URL_PREDICATE_VARS_ATTR = "urlPredicateVars";

	private PathMatcher pathMatcher = new ParsingPathMatcher();
	private HttpRequestPathHelper pathHelper = new HttpRequestPathHelper();

	public PathMatcher getPathMatcher() {
		return pathMatcher;
	}

	public void setPathMatcher(PathMatcher pathMatcher) {
		this.pathMatcher = pathMatcher;
	}

	public HttpRequestPathHelper getPathHelper() {
		return pathHelper;
	}

	public void setPathHelper(HttpRequestPathHelper pathHelper) {
		this.pathHelper = pathHelper;
	}

	@Override
	public Predicate<ServerWebExchange> apply(String... args) {
		validate(1, args);
		String pattern = args[0];

		return exchange -> {
			String lookupPath = getPathHelper().getLookupPathForRequest(exchange);
			boolean match = getPathMatcher().match(pattern, lookupPath);
			if (match) {
				Map<String, String> variables = getPathMatcher().extractUriTemplateVariables(pattern, lookupPath);
				exchange.getAttributes().put(URL_PREDICATE_VARS_ATTR, variables);
			}
			return match;
			//TODO: support trailingSlashMatch
		};
	}
}
