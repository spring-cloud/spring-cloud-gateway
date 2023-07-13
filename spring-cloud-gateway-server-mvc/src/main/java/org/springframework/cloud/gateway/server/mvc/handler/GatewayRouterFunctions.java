/*
 * Copyright 2013-2023 the original author or authors.
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

package org.springframework.cloud.gateway.server.mvc.handler;

import org.springframework.web.servlet.function.HandlerFunction;
import org.springframework.web.servlet.function.RequestPredicate;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.RouterFunctions;
import org.springframework.web.servlet.function.RouterFunctions.Builder;
import org.springframework.web.servlet.function.ServerResponse;

import static org.springframework.cloud.gateway.server.mvc.filter.BeforeFilterFunctions.routeId;

public abstract class GatewayRouterFunctions {

	private GatewayRouterFunctions() {
	}

	public static Builder route() {
		return RouterFunctions.route();
	}

	public static Builder route(String routeId) {
		Builder builder = RouterFunctions.route().before(routeId(routeId));
		return new GatewayRouterFunctionsBuilder(builder, routeId);
	}

	public static <T extends ServerResponse> RouterFunction<T> route(RequestPredicate predicate,
			HandlerFunction<T> handlerFunction) {
		return RouterFunctions.route(predicate, handlerFunction);
	}

}
