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

import org.springframework.cloud.gateway.handler.support.ExchangeServerRequest;
import org.springframework.tuple.Tuple;
import org.springframework.web.reactive.function.server.RequestPredicate;
import org.springframework.web.reactive.function.server.RequestPredicates;

import java.util.Arrays;
import java.util.List;

/**
 * @author Spencer Gibb
 */
public class QueryRequestPredicateFactory implements RequestPredicateFactory {

	public static final String PARAM_KEY = "param";
	public static final String REGEXP_KEY = "regexp";

	@Override
	public List<String> argNames() {
		return Arrays.asList(PARAM_KEY, REGEXP_KEY);
	}

	@Override
	public boolean validateArgSize() {
		return false;
	}

	@Override
	public RequestPredicate apply(Tuple args) {
		validate(1, args);
		String param = args.getString(PARAM_KEY);

		if (!args.hasFieldName(REGEXP_KEY)) {
			return req -> {
				//TODO: ServerRequest support for query params with no value
				ExchangeServerRequest request = (ExchangeServerRequest) req;
				return request.exchange().getRequest().getQueryParams().containsKey(param);
			};
		}

		String regexp = args.getString(REGEXP_KEY);

		return RequestPredicates.queryParam(param, value -> value.matches(regexp));
	}
}
