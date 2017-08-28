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
import java.util.function.Predicate;

import org.springframework.tuple.Tuple;
import org.springframework.web.server.ServerWebExchange;

/**
 * @author Spencer Gibb
 */
public class QueryRoutePredicateFactory implements RoutePredicateFactory {

	public static final String PARAM_KEY = "param";
	public static final String REGEXP_KEY = "regexp";

	@Override
	public List<String> argNames() {
		return Arrays.asList(PARAM_KEY, REGEXP_KEY);
	}

	@Override
	public boolean validateArgs() {
		return false;
	}

	@Override
	public Predicate<ServerWebExchange> apply(Tuple args) {
		validateMin(1, args);
		String param = args.getString(PARAM_KEY);

		return exchange -> {
			if (!args.hasFieldName(REGEXP_KEY)) {
				// check existence of header
				return exchange.getRequest().getQueryParams().containsKey(param);
			}

			String regexp = args.getString(REGEXP_KEY);

			List<String> values = exchange.getRequest().getQueryParams().get(param);
			for (String value : values) {
				if (value.matches(regexp)) {
					return true;
				}
			}
			return false;
		};
	}
}
