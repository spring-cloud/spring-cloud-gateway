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

import java.net.URI;
import java.util.Arrays;
import java.util.Collections;
import java.util.function.Function;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.cloud.gateway.server.mvc.common.MvcUtils;
import org.springframework.cloud.gateway.server.mvc.config.RouteProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.web.servlet.function.HandlerFilterFunction;
import org.springframework.web.servlet.function.HandlerFunction;
import org.springframework.web.servlet.function.ServerResponse;

@AutoConfiguration
public class HandlerFunctionAutoConfiguration {

	@Bean
	public Function<RouteProperties, HandlerFunctionDefinition> fnHandlerFunctionDefinition() {
		return routeProperties -> new HandlerFunctionDefinition.Default("fn",
				HandlerFunctions.fn(routeProperties.getUri().getSchemeSpecificPart()));
	}

	@Bean
	public Function<RouteProperties, HandlerFunctionDefinition> forwardHandlerFunctionDefinition() {
		return routeProperties -> new HandlerFunctionDefinition.Default("forward",
				HandlerFunctions.forward(routeProperties.getUri().getPath()));
	}

	@Bean
	public Function<RouteProperties, HandlerFunctionDefinition> httpHandlerFunctionDefinition() {
		return routeProperties -> getResult("http", routeProperties.getId(), routeProperties.getUri(),
				HandlerFunctions.http());
	}

	@Bean
	public Function<RouteProperties, HandlerFunctionDefinition> httpsHandlerFunctionDefinition() {
		return routeProperties -> getResult("https", routeProperties.getId(), routeProperties.getUri(),
				HandlerFunctions.https());
	}

	@Bean
	public Function<RouteProperties, HandlerFunctionDefinition> noHandlerFunctionDefinition() {
		return routeProperties -> getResult("no", routeProperties.getId(), routeProperties.getUri(),
				HandlerFunctions.no());
	}

	@Bean
	public Function<RouteProperties, HandlerFunctionDefinition> streamHandlerFunctionDefinition() {
		return routeProperties -> new HandlerFunctionDefinition.Default("stream",
				HandlerFunctions.stream(routeProperties.getUri().getSchemeSpecificPart()));
	}

	private static HandlerFunctionDefinition getResult(String scheme, String id, URI uri,
			HandlerFunction<ServerResponse> handlerFunction) {
		HandlerFilterFunction<ServerResponse, ServerResponse> setId = setIdFilter(id);
		HandlerFilterFunction<ServerResponse, ServerResponse> setRequest = setRequestUrlFilter(uri);
		return new HandlerFunctionDefinition.Default(scheme, handlerFunction, Arrays.asList(setId, setRequest),
				Collections.emptyList());
	}

	private static HandlerFilterFunction<ServerResponse, ServerResponse> setIdFilter(String id) {
		return (request, next) -> {
			MvcUtils.setRouteId(request, id);
			return next.handle(request);
		};
	}

	private static HandlerFilterFunction<ServerResponse, ServerResponse> setRequestUrlFilter(URI uri) {
		return (request, next) -> {
			MvcUtils.setRequestUrl(request, uri);
			return next.handle(request);
		};
	}

}
