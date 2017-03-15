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

import org.springframework.tuple.Tuple;
import org.springframework.web.reactive.function.server.RequestPredicate;
import org.springframework.web.reactive.function.server.RequestPredicates;
import org.springframework.web.util.patterns.PathPatternParser;

import java.util.Collections;
import java.util.List;

/**
 * @author Spencer Gibb
 */
public class PathRequestPredicateFactory implements RequestPredicateFactory {

	public static final String PATTERN_KEY = "pattern";

	private PathPatternParser pathPatternParser;

	public void setPathPatternParser(PathPatternParser pathPatternParser) {
		this.pathPatternParser = pathPatternParser;
	}

	@Override
	public List<String> argNames() {
		return Collections.singletonList(PATTERN_KEY);
	}

	@Override
	public RequestPredicate apply(Tuple args) {
		String pattern = args.getString(PATTERN_KEY);

		if (this.pathPatternParser != null) {
			return RequestPredicates.pathPredicates(this.pathPatternParser).apply(pattern);
		}

		return RequestPredicates.path(pattern);
	}
}
