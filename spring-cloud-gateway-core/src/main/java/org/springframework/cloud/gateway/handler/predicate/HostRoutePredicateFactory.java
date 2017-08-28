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
import java.util.function.Predicate;

import org.springframework.tuple.Tuple;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.PathMatcher;
import org.springframework.web.server.ServerWebExchange;

/**
 * @author Spencer Gibb
 */
public class HostRoutePredicateFactory implements RoutePredicateFactory {

	private PathMatcher pathMatcher = new AntPathMatcher(".");

	public void setPathMatcher(PathMatcher pathMatcher) {
		this.pathMatcher = pathMatcher;
	}

	@Override
	public List<String> argNames() {
		return Collections.singletonList(PATTERN_KEY);
	}

	@Override
	public Predicate<ServerWebExchange> apply(Tuple args) {
		String pattern = args.getString(PATTERN_KEY);

		return exchange -> {
			String host = exchange.getRequest().getHeaders().getFirst("Host");
			return this.pathMatcher.match(pattern, host);
		};
	}
}
