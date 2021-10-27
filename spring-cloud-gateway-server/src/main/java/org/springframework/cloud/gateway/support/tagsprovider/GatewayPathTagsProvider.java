/*
 * Copyright 2013-2020 the original author or authors.
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
 */

package org.springframework.cloud.gateway.support.tagsprovider;

import io.micrometer.core.instrument.Tags;

import org.springframework.cloud.gateway.route.Route;
import org.springframework.web.server.ServerWebExchange;

import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR;

/**
 * @author Marta Medio
 * @author Alberto C. Ríos
 */
public class GatewayPathTagsProvider implements GatewayTagsProvider {

	private static final String START_PATH_PATTERN = "Paths: [";

	private static final String END_PATH_PATTERN = "], match";

	@Override
	public Tags apply(ServerWebExchange exchange) {
		Route route = exchange.getAttribute(GATEWAY_ROUTE_ATTR);

		if (route != null) {
			String rawPredicate = route.getPredicate().toString();
			if (predicateContainsPath(rawPredicate)) {
				int beginIndex = rawPredicate.indexOf(START_PATH_PATTERN) + START_PATH_PATTERN.length();
				String predicate = rawPredicate.substring(beginIndex, rawPredicate.indexOf(END_PATH_PATTERN));
				return Tags.of("path", predicate);
			}
		}

		return Tags.empty();
	}

	private boolean predicateContainsPath(String rawPredicate) {
		return rawPredicate != null && rawPredicate.contains(START_PATH_PATTERN)
				&& rawPredicate.contains(END_PATH_PATTERN);
	}

}
