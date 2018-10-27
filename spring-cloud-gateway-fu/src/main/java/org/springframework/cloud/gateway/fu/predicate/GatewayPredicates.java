/*
 * Copyright 2013-2018 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.cloud.gateway.fu.predicate;

import org.springframework.util.AntPathMatcher;
import org.springframework.util.PathMatcher;
import org.springframework.web.reactive.function.server.RequestPredicate;

public abstract class GatewayPredicates {

	public static final PathMatcher HOST_PATH_MATCHER = new AntPathMatcher(".");

	private GatewayPredicates() {
	}

	public static RequestPredicate host(String pattern) {
		return host(HOST_PATH_MATCHER, pattern);
	}

	public static RequestPredicate host(PathMatcher pathMatcher, String pattern) {
		return request -> {
			String host = request.headers().asHttpHeaders().getFirst("Host");
			return pathMatcher.match(pattern, host);
		};
	}
}
