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

import java.util.Map;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

import org.springframework.cloud.gateway.server.mvc.common.MvcUtils;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.web.servlet.function.HandlerFilterFunction;
import org.springframework.web.servlet.function.HandlerFunction;
import org.springframework.web.servlet.function.RequestPredicate;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.RouterFunctions;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;

public class GatewayRouterFunctionsBuilder implements RouterFunctions.Builder {

	private final RouterFunctions.Builder builder;

	private final String routeId;

	public GatewayRouterFunctionsBuilder(RouterFunctions.Builder builder, String routeId) {
		this.builder = builder;
		this.routeId = routeId;
	}

	@Override
	public RouterFunction<ServerResponse> build() {
		RouterFunction<ServerResponse> routerFunction = builder.build();
		RouterFunction<ServerResponse> withAttributes = routerFunction.withAttribute(MvcUtils.GATEWAY_ROUTE_ID_ATTR,
				this.routeId);
		return new GatewayDelegatingRouterFunction<>(withAttributes, this.routeId);
	}

	@Override
	public RouterFunctions.Builder GET(HandlerFunction<ServerResponse> handlerFunction) {
		builder.GET(handlerFunction);
		return this;
	}

	@Override
	public RouterFunctions.Builder GET(String pattern, HandlerFunction<ServerResponse> handlerFunction) {
		builder.GET(pattern, handlerFunction);
		return this;
	}

	@Override
	public RouterFunctions.Builder GET(RequestPredicate predicate, HandlerFunction<ServerResponse> handlerFunction) {
		builder.GET(predicate, handlerFunction);
		return this;
	}

	@Override
	public RouterFunctions.Builder GET(String pattern, RequestPredicate predicate,
			HandlerFunction<ServerResponse> handlerFunction) {
		builder.GET(pattern, predicate, handlerFunction);
		return this;
	}

	@Override
	public RouterFunctions.Builder HEAD(HandlerFunction<ServerResponse> handlerFunction) {
		builder.HEAD(handlerFunction);
		return this;
	}

	@Override
	public RouterFunctions.Builder HEAD(String pattern, HandlerFunction<ServerResponse> handlerFunction) {
		builder.HEAD(pattern, handlerFunction);
		return this;
	}

	@Override
	public RouterFunctions.Builder HEAD(RequestPredicate predicate, HandlerFunction<ServerResponse> handlerFunction) {
		builder.HEAD(predicate, handlerFunction);
		return this;
	}

	@Override
	public RouterFunctions.Builder HEAD(String pattern, RequestPredicate predicate,
			HandlerFunction<ServerResponse> handlerFunction) {
		builder.HEAD(pattern, predicate, handlerFunction);
		return this;
	}

	@Override
	public RouterFunctions.Builder POST(HandlerFunction<ServerResponse> handlerFunction) {
		builder.POST(handlerFunction);
		return this;
	}

	@Override
	public RouterFunctions.Builder POST(String pattern, HandlerFunction<ServerResponse> handlerFunction) {
		builder.POST(pattern, handlerFunction);
		return this;
	}

	@Override
	public RouterFunctions.Builder POST(RequestPredicate predicate, HandlerFunction<ServerResponse> handlerFunction) {
		builder.POST(predicate, handlerFunction);
		return this;
	}

	@Override
	public RouterFunctions.Builder POST(String pattern, RequestPredicate predicate,
			HandlerFunction<ServerResponse> handlerFunction) {
		builder.POST(pattern, predicate, handlerFunction);
		return this;
	}

	@Override
	public RouterFunctions.Builder PUT(HandlerFunction<ServerResponse> handlerFunction) {
		builder.PUT(handlerFunction);
		return this;
	}

	@Override
	public RouterFunctions.Builder PUT(String pattern, HandlerFunction<ServerResponse> handlerFunction) {
		builder.PUT(pattern, handlerFunction);
		return this;
	}

	@Override
	public RouterFunctions.Builder PUT(RequestPredicate predicate, HandlerFunction<ServerResponse> handlerFunction) {
		builder.PUT(predicate, handlerFunction);
		return this;
	}

	@Override
	public RouterFunctions.Builder PUT(String pattern, RequestPredicate predicate,
			HandlerFunction<ServerResponse> handlerFunction) {
		builder.PUT(pattern, predicate, handlerFunction);
		return this;
	}

	@Override
	public RouterFunctions.Builder PATCH(HandlerFunction<ServerResponse> handlerFunction) {
		builder.PATCH(handlerFunction);
		return this;
	}

	@Override
	public RouterFunctions.Builder PATCH(String pattern, HandlerFunction<ServerResponse> handlerFunction) {
		builder.PATCH(pattern, handlerFunction);
		return this;
	}

	@Override
	public RouterFunctions.Builder PATCH(RequestPredicate predicate, HandlerFunction<ServerResponse> handlerFunction) {
		builder.PATCH(predicate, handlerFunction);
		return this;
	}

	@Override
	public RouterFunctions.Builder PATCH(String pattern, RequestPredicate predicate,
			HandlerFunction<ServerResponse> handlerFunction) {
		builder.PATCH(pattern, predicate, handlerFunction);
		return this;
	}

	@Override
	public RouterFunctions.Builder DELETE(HandlerFunction<ServerResponse> handlerFunction) {
		builder.DELETE(handlerFunction);
		return this;
	}

	@Override
	public RouterFunctions.Builder DELETE(String pattern, HandlerFunction<ServerResponse> handlerFunction) {
		builder.DELETE(pattern, handlerFunction);
		return this;
	}

	@Override
	public RouterFunctions.Builder DELETE(RequestPredicate predicate, HandlerFunction<ServerResponse> handlerFunction) {
		builder.DELETE(predicate, handlerFunction);
		return this;
	}

	@Override
	public RouterFunctions.Builder DELETE(String pattern, RequestPredicate predicate,
			HandlerFunction<ServerResponse> handlerFunction) {
		builder.DELETE(pattern, predicate, handlerFunction);
		return this;
	}

	@Override
	public RouterFunctions.Builder OPTIONS(HandlerFunction<ServerResponse> handlerFunction) {
		builder.OPTIONS(handlerFunction);
		return this;
	}

	@Override
	public RouterFunctions.Builder OPTIONS(String pattern, HandlerFunction<ServerResponse> handlerFunction) {
		builder.OPTIONS(pattern, handlerFunction);
		return this;
	}

	@Override
	public RouterFunctions.Builder OPTIONS(RequestPredicate predicate,
			HandlerFunction<ServerResponse> handlerFunction) {
		builder.OPTIONS(predicate, handlerFunction);
		return this;
	}

	@Override
	public RouterFunctions.Builder OPTIONS(String pattern, RequestPredicate predicate,
			HandlerFunction<ServerResponse> handlerFunction) {
		builder.OPTIONS(pattern, predicate, handlerFunction);
		return this;
	}

	@Override
	public RouterFunctions.Builder route(RequestPredicate predicate, HandlerFunction<ServerResponse> handlerFunction) {
		builder.route(predicate, handlerFunction);
		return this;
	}

	@Override
	public RouterFunctions.Builder add(RouterFunction<ServerResponse> routerFunction) {
		builder.add(routerFunction);
		return this;
	}

	@Override
	public RouterFunctions.Builder resource(RequestPredicate predicate, Resource resource) {
		return builder.resource(predicate, resource);
	}

	@Override
	public RouterFunctions.Builder resource(RequestPredicate predicate, Resource resource,
			BiConsumer<Resource, HttpHeaders> headersConsumer) {
		return builder.resource(predicate, resource, headersConsumer);
	}

	@Override
	public RouterFunctions.Builder resources(String pattern, Resource location) {
		builder.resources(pattern, location);
		return this;
	}

	@Override
	public RouterFunctions.Builder resources(Function<ServerRequest, Optional<Resource>> lookupFunction) {
		builder.resources(lookupFunction);
		return this;
	}

	@Override
	public RouterFunctions.Builder resources(String pattern, Resource location,
			BiConsumer<Resource, HttpHeaders> headersConsumer) {
		builder.resources(pattern, location, headersConsumer);
		return this;
	}

	@Override
	public RouterFunctions.Builder resources(Function<ServerRequest, Optional<Resource>> lookupFunction,
			BiConsumer<Resource, HttpHeaders> headersConsumer) {
		builder.resources(lookupFunction, headersConsumer);
		return this;
	}

	@Override
	public RouterFunctions.Builder nest(RequestPredicate predicate,
			Supplier<RouterFunction<ServerResponse>> routerFunctionSupplier) {
		builder.nest(predicate, routerFunctionSupplier);
		return this;
	}

	@Override
	public RouterFunctions.Builder nest(RequestPredicate predicate, Consumer<RouterFunctions.Builder> builderConsumer) {
		builder.nest(predicate, builderConsumer);
		return this;
	}

	@Override
	public RouterFunctions.Builder path(String pattern,
			Supplier<RouterFunction<ServerResponse>> routerFunctionSupplier) {
		builder.path(pattern, routerFunctionSupplier);
		return this;
	}

	@Override
	public RouterFunctions.Builder path(String pattern, Consumer<RouterFunctions.Builder> builderConsumer) {
		builder.path(pattern, builderConsumer);
		return this;
	}

	@Override
	public RouterFunctions.Builder filter(HandlerFilterFunction<ServerResponse, ServerResponse> filterFunction) {
		builder.filter(filterFunction);
		return this;
	}

	@Override
	public RouterFunctions.Builder before(Function<ServerRequest, ServerRequest> requestProcessor) {
		builder.before(requestProcessor);
		return this;
	}

	@Override
	public RouterFunctions.Builder after(BiFunction<ServerRequest, ServerResponse, ServerResponse> responseProcessor) {
		builder.after(responseProcessor);
		return this;
	}

	@Override
	public RouterFunctions.Builder onError(Predicate<Throwable> predicate,
			BiFunction<Throwable, ServerRequest, ServerResponse> responseProvider) {
		builder.onError(predicate, responseProvider);
		return this;
	}

	@Override
	public RouterFunctions.Builder onError(Class<? extends Throwable> exceptionType,
			BiFunction<Throwable, ServerRequest, ServerResponse> responseProvider) {
		builder.onError(exceptionType, responseProvider);
		return this;
	}

	@Override
	public RouterFunctions.Builder withAttribute(String name, Object value) {
		builder.withAttribute(name, value);
		return this;
	}

	@Override
	public RouterFunctions.Builder withAttributes(Consumer<Map<String, Object>> attributesConsumer) {
		builder.withAttributes(attributesConsumer);
		return this;
	}

}
