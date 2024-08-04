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

import java.util.HashMap;
import java.util.Optional;

import org.springframework.cloud.gateway.server.mvc.common.MvcUtils;
import org.springframework.web.servlet.function.HandlerFunction;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.RouterFunctions;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;

// no need for all methods delegated since there are default implementations
// adds routeId to request attributes in route(ServerRequest)
public class GatewayDelegatingRouterFunction<T extends ServerResponse> implements RouterFunction<T> {

	private final RouterFunction<T> delegate;

	private final String routeId;

	public GatewayDelegatingRouterFunction(RouterFunction<T> delegate, String routeId) {
		this.delegate = delegate;
		this.routeId = routeId;
	}

	@Override
	public Optional<HandlerFunction<T>> route(ServerRequest request) {
		// Don't use MvcUtils.putAttribute() as it is prior to init of gateway attrs
		request.attributes().put(MvcUtils.GATEWAY_ROUTE_ID_ATTR, routeId);
		request.attributes().computeIfAbsent(MvcUtils.GATEWAY_ATTRIBUTES_ATTR, s -> new HashMap<String, Object>());
		Optional<HandlerFunction<T>> handlerFunction = delegate.route(request);
		request.attributes().remove(MvcUtils.GATEWAY_ROUTE_ID_ATTR);
		return handlerFunction;
	}

	@Override
	public void accept(RouterFunctions.Visitor visitor) {
		delegate.accept(visitor);
	}

	@Override
	public String toString() {
		return String.format("RouterFunction routeId=%s delegate=%s", routeId, delegate);
	}

}
