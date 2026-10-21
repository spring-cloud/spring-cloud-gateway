/*
 * Copyright 2013-present the original author or authors.
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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Stream;

import org.springframework.cloud.gateway.server.mvc.common.MvcUtils;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.util.Assert;
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

	private final List<HandlerFilterFunction<ServerResponse, ServerResponse>> filters = new ArrayList<>();

	private final List<HandlerFilterFunction<ServerResponse, ServerResponse>> errorHandlers = new ArrayList<>();

	public GatewayRouterFunctionsBuilder(RouterFunctions.Builder builder, String routeId) {
		this.builder = builder;
		this.routeId = routeId;
	}

	@Override
	public RouterFunction<ServerResponse> build() {
		RouterFunction<ServerResponse> routerFunction = builder.build();
		Optional<HandlerFilterFunction<ServerResponse, ServerResponse>> filter = Stream
			.concat(this.filters.stream(), this.errorHandlers.stream())
			.reduce(HandlerFilterFunction::andThen);
		if (filter.isPresent()) {
			routerFunction = routerFunction.filter(filter.get());
		}
		RouterFunction<ServerResponse> withAttributes = routerFunction.withAttribute(MvcUtils.GATEWAY_ROUTE_ID_ATTR,
				this.routeId);
		return new GatewayDelegatingRouterFunction<>(withAttributes, this.routeId);
	}

	@Override
	public <T extends ServerResponse> RouterFunctions.Builder GET(HandlerFunction<T> handlerFunction) {
		builder.GET(handlerFunction);
		return this;
	}

	@Override
	public <T extends ServerResponse> RouterFunctions.Builder GET(String pattern, HandlerFunction<T> handlerFunction) {
		builder.GET(pattern, handlerFunction);
		return this;
	}

	@Override
	public <T extends ServerResponse> RouterFunctions.Builder GET(RequestPredicate predicate,
			HandlerFunction<T> handlerFunction) {
		builder.GET(predicate, handlerFunction);
		return this;
	}

	@Override
	public <T extends ServerResponse> RouterFunctions.Builder GET(String pattern, RequestPredicate predicate,
			HandlerFunction<T> handlerFunction) {
		builder.GET(pattern, predicate, handlerFunction);
		return this;
	}

	@Override
	public <T extends ServerResponse> RouterFunctions.Builder HEAD(HandlerFunction<T> handlerFunction) {
		builder.HEAD(handlerFunction);
		return this;
	}

	@Override
	public <T extends ServerResponse> RouterFunctions.Builder HEAD(String pattern, HandlerFunction<T> handlerFunction) {
		builder.HEAD(pattern, handlerFunction);
		return this;
	}

	@Override
	public <T extends ServerResponse> RouterFunctions.Builder HEAD(RequestPredicate predicate,
			HandlerFunction<T> handlerFunction) {
		builder.HEAD(predicate, handlerFunction);
		return this;
	}

	@Override
	public <T extends ServerResponse> RouterFunctions.Builder HEAD(String pattern, RequestPredicate predicate,
			HandlerFunction<T> handlerFunction) {
		builder.HEAD(pattern, predicate, handlerFunction);
		return this;
	}

	@Override
	public <T extends ServerResponse> RouterFunctions.Builder POST(HandlerFunction<T> handlerFunction) {
		builder.POST(handlerFunction);
		return this;
	}

	@Override
	public <T extends ServerResponse> RouterFunctions.Builder POST(String pattern, HandlerFunction<T> handlerFunction) {
		builder.POST(pattern, handlerFunction);
		return this;
	}

	@Override
	public <T extends ServerResponse> RouterFunctions.Builder POST(RequestPredicate predicate,
			HandlerFunction<T> handlerFunction) {
		builder.POST(predicate, handlerFunction);
		return this;
	}

	@Override
	public <T extends ServerResponse> RouterFunctions.Builder POST(String pattern, RequestPredicate predicate,
			HandlerFunction<T> handlerFunction) {
		builder.POST(pattern, predicate, handlerFunction);
		return this;
	}

	@Override
	public <T extends ServerResponse> RouterFunctions.Builder PUT(HandlerFunction<T> handlerFunction) {
		builder.PUT(handlerFunction);
		return this;
	}

	@Override
	public <T extends ServerResponse> RouterFunctions.Builder PUT(String pattern, HandlerFunction<T> handlerFunction) {
		builder.PUT(pattern, handlerFunction);
		return this;
	}

	@Override
	public <T extends ServerResponse> RouterFunctions.Builder PUT(RequestPredicate predicate,
			HandlerFunction<T> handlerFunction) {
		builder.PUT(predicate, handlerFunction);
		return this;
	}

	@Override
	public <T extends ServerResponse> RouterFunctions.Builder PUT(String pattern, RequestPredicate predicate,
			HandlerFunction<T> handlerFunction) {
		builder.PUT(pattern, predicate, handlerFunction);
		return this;
	}

	@Override
	public <T extends ServerResponse> RouterFunctions.Builder PATCH(HandlerFunction<T> handlerFunction) {
		builder.PATCH(handlerFunction);
		return this;
	}

	@Override
	public <T extends ServerResponse> RouterFunctions.Builder PATCH(String pattern,
			HandlerFunction<T> handlerFunction) {
		builder.PATCH(pattern, handlerFunction);
		return this;
	}

	@Override
	public <T extends ServerResponse> RouterFunctions.Builder PATCH(RequestPredicate predicate,
			HandlerFunction<T> handlerFunction) {
		builder.PATCH(predicate, handlerFunction);
		return this;
	}

	@Override
	public <T extends ServerResponse> RouterFunctions.Builder PATCH(String pattern, RequestPredicate predicate,
			HandlerFunction<T> handlerFunction) {
		builder.PATCH(pattern, predicate, handlerFunction);
		return this;
	}

	@Override
	public <T extends ServerResponse> RouterFunctions.Builder DELETE(HandlerFunction<T> handlerFunction) {
		builder.DELETE(handlerFunction);
		return this;
	}

	@Override
	public <T extends ServerResponse> RouterFunctions.Builder DELETE(String pattern,
			HandlerFunction<T> handlerFunction) {
		builder.DELETE(pattern, handlerFunction);
		return this;
	}

	@Override
	public <T extends ServerResponse> RouterFunctions.Builder DELETE(RequestPredicate predicate,
			HandlerFunction<T> handlerFunction) {
		builder.DELETE(predicate, handlerFunction);
		return this;
	}

	@Override
	public <T extends ServerResponse> RouterFunctions.Builder DELETE(String pattern, RequestPredicate predicate,
			HandlerFunction<T> handlerFunction) {
		builder.DELETE(pattern, predicate, handlerFunction);
		return this;
	}

	@Override
	public <T extends ServerResponse> RouterFunctions.Builder OPTIONS(HandlerFunction<T> handlerFunction) {
		builder.OPTIONS(handlerFunction);
		return this;
	}

	@Override
	public <T extends ServerResponse> RouterFunctions.Builder OPTIONS(String pattern,
			HandlerFunction<T> handlerFunction) {
		builder.OPTIONS(pattern, handlerFunction);
		return this;
	}

	@Override
	public <T extends ServerResponse> RouterFunctions.Builder OPTIONS(RequestPredicate predicate,
			HandlerFunction<T> handlerFunction) {
		builder.OPTIONS(predicate, handlerFunction);
		return this;
	}

	@Override
	public <T extends ServerResponse> RouterFunctions.Builder OPTIONS(String pattern, RequestPredicate predicate,
			HandlerFunction<T> handlerFunction) {
		builder.OPTIONS(pattern, predicate, handlerFunction);
		return this;
	}

	@Override
	public <T extends ServerResponse> RouterFunctions.Builder route(RequestPredicate predicate,
			HandlerFunction<T> handlerFunction) {
		builder.route(predicate, handlerFunction);
		return this;
	}

	@Override
	public <T extends ServerResponse> RouterFunctions.Builder add(RouterFunction<T> routerFunction) {
		builder.add(routerFunction);
		return this;
	}

	@Override
	public RouterFunctions.Builder resource(RequestPredicate predicate, Resource resource) {
		builder.resource(predicate, resource);
		return this;
	}

	@Override
	public RouterFunctions.Builder resource(RequestPredicate predicate, Resource resource,
			BiConsumer<Resource, HttpHeaders> headersConsumer) {
		builder.resource(predicate, resource, headersConsumer);
		return this;
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
	public <T extends ServerResponse> RouterFunctions.Builder nest(RequestPredicate predicate,
			Supplier<RouterFunction<T>> routerFunctionSupplier) {
		builder.nest(predicate, routerFunctionSupplier);
		return this;
	}

	@Override
	public RouterFunctions.Builder nest(RequestPredicate predicate, Consumer<RouterFunctions.Builder> builderConsumer) {
		Assert.notNull(builderConsumer, "BuilderConsumer must not be null");
		builder.nest(predicate, () -> {
			GatewayRouterFunctionsBuilder nested = new GatewayRouterFunctionsBuilder(RouterFunctions.route(),
					this.routeId);
			builderConsumer.accept(nested);
			return nested.build();
		});
		return this;
	}

	@Override
	public <T extends ServerResponse> RouterFunctions.Builder path(String pattern,
			Supplier<RouterFunction<T>> routerFunctionSupplier) {
		builder.path(pattern, routerFunctionSupplier);
		return this;
	}

	@Override
	public RouterFunctions.Builder path(String pattern, Consumer<RouterFunctions.Builder> builderConsumer) {
		Assert.notNull(builderConsumer, "BuilderConsumer must not be null");
		builder.path(pattern, () -> {
			GatewayRouterFunctionsBuilder nested = new GatewayRouterFunctionsBuilder(RouterFunctions.route(),
					this.routeId);
			builderConsumer.accept(nested);
			return nested.build();
		});
		return this;
	}

	@Override
	@SuppressWarnings("unchecked")
	public <T extends ServerResponse, R extends ServerResponse> RouterFunctions.Builder filter(
			HandlerFilterFunction<T, R> filterFunction) {
		Assert.notNull(filterFunction, "FilterFunction must not be null");
		HandlerFilterFunction<ServerResponse, ServerResponse> adaptingFilter = (request, next) -> {
			HandlerFunction<T> adaptingNext = downstreamRequest -> (T) GatewayServerResponse
				.adapt(next.handle(downstreamRequest));
			return GatewayServerResponse.adapt(filterFunction.filter(request, adaptingNext));
		};
		this.filters.add(adaptingFilter);
		return this;
	}

	@Override
	public RouterFunctions.Builder before(Function<ServerRequest, ServerRequest> requestProcessor) {
		this.filters.add(HandlerFilterFunction.ofRequestProcessor(requestProcessor));
		return this;
	}

	@Override
	public <T extends ServerResponse, R extends ServerResponse> RouterFunctions.Builder after(
			BiFunction<ServerRequest, T, R> responseProcessor) {
		return filter(HandlerFilterFunction.ofResponseProcessor(responseProcessor));
	}

	@Override
	public <T extends ServerResponse> RouterFunctions.Builder onError(Predicate<Throwable> predicate,
			BiFunction<Throwable, ServerRequest, T> responseProvider) {
		Assert.notNull(responseProvider, "ResponseProvider must not be null");
		HandlerFilterFunction<ServerResponse, ServerResponse> errorFilter = HandlerFilterFunction
			.ofErrorHandler(predicate, (failure, request) -> responseProvider.apply(failure, request));
		this.errorHandlers.add(0, (request, next) -> errorFilter.filter(request, downstreamRequest -> {
			ServerResponse response = next.handle(downstreamRequest);
			if (response instanceof DelegatingServerResponse delegatingResponse) {
				// Spring registers write errors only on its own response base class.
				errorFilter.filter(downstreamRequest, ignored -> delegatingResponse.delegate());
			}
			return response;
		}));
		return this;
	}

	@Override
	public <T extends ServerResponse> RouterFunctions.Builder onError(Class<? extends Throwable> exceptionType,
			BiFunction<Throwable, ServerRequest, T> responseProvider) {
		Assert.notNull(exceptionType, "ExceptionType must not be null");
		return onError(exceptionType::isInstance, responseProvider);
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
