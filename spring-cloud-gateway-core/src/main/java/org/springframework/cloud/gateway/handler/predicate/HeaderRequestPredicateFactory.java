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

import java.util.Arrays;
import java.util.List;

import org.springframework.tuple.Tuple;
import org.springframework.web.reactive.function.server.RequestPredicate;
import org.springframework.web.reactive.function.server.RequestPredicates;

/**
 * @author Spencer Gibb
 */
public class HeaderRequestPredicateFactory implements RequestPredicateFactory {

	public static final String HEADER_KEY = "header";
	public static final String REGEXP_KEY = "regexp";

	@Override
	public List<String> argNames() {
		return Arrays.asList(HEADER_KEY, REGEXP_KEY);
	}

	@Override
	public RequestPredicate apply(Tuple args) {
		String header = args.getString(HEADER_KEY);
		String regexp = args.getString(REGEXP_KEY);

		return RequestPredicates.headers(headers -> {
			List<String> values = headers.asHttpHeaders().get(header);
			for (String value : values) {
				if (value.matches(regexp)) {
					return true;
				}
			}
			return false;
		});
	}
}
