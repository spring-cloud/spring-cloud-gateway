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

package org.springframework.cloud.gateway.handler;

import reactor.core.publisher.Mono;

import org.springframework.web.reactive.handler.AbstractHandlerMapping;
import org.springframework.web.server.ServerWebExchange;

/**
 * Custom route resolution processor
 * <p>
 * In some scenarios, using a user-defined routing resolution strategy will be faster than
 * a general regular resolution strategy, so SCG will provide this ability to customize
 * routing resolution.
 *
 * @author: yizhenqiang
 * @date: 2021/4/28 21:24
 */
public abstract class AbstractCustomizeRouteResolveHandlerMapping extends AbstractHandlerMapping {

	/**
	 * The key used to store the result of custom routing analysis.
	 */
	public static final String CUSTOMIZE_ROUTE_DEFINITION_ID_KEY = "CUSTOMIZE_ROUTE_DEFINITION_ID_KEY";

	public AbstractCustomizeRouteResolveHandlerMapping() {
		/**
		 * We need to ensure that it is executed before
		 * {@link RoutePredicateHandlerMapping}
		 */
		setOrder(0);
	}

	@Override
	protected Mono<?> getHandlerInternal(ServerWebExchange serverWebExchange) {
		String customizeRouteDefinitionId = resolveRouteId(serverWebExchange);
		if (customizeRouteDefinitionId != null) {
			serverWebExchange.getAttributes().put(CUSTOMIZE_ROUTE_DEFINITION_ID_KEY, customizeRouteDefinitionId);
		}

		return Mono.empty();
	}

	/**
	 * The user-defined routing information is parsed through {@link ServerWebExchange}
	 * <p>
	 * You can use this method again to resolve a routing ID through server webexchange
	 * according to your own routing rules.
	 * @param serverWebExchange the web server context.
	 * @return Route definition ID resolved according to custom routing rules.
	 */
	protected String resolveRouteId(ServerWebExchange serverWebExchange) {
		return null;
	}
}
