/*
 * Copyright 2013-2025 the original author or authors.
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

import java.lang.reflect.Method;
import java.net.URI;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;

import org.springframework.cloud.gateway.server.mvc.common.MvcUtils;
import org.springframework.cloud.gateway.server.mvc.config.RouteProperties;
import org.springframework.web.servlet.function.HandlerFilterFunction;
import org.springframework.web.servlet.function.HandlerFunction;
import org.springframework.web.servlet.function.ServerResponse;

class DefaultHandlerSupplier implements HandlerSupplier {

	@Override
	public Collection<Method> get() {
		return Arrays.asList(getClass().getMethods());
	}

	public static HandlerDiscoverer.Result fn(RouteProperties routeProperties) {
		// fn:fnName
		return fn(routeProperties.getUri().getSchemeSpecificPart());
	}

	public static HandlerDiscoverer.Result fn(String functionName) {
		return new HandlerDiscoverer.Result(HandlerFunctions.fn(functionName), Collections.emptyList(),
				Collections.emptyList());
	}

	public static HandlerDiscoverer.Result forward(RouteProperties routeProperties) {
		return forward(routeProperties.getId(), routeProperties.getUri());
	}

	public static HandlerDiscoverer.Result forward(String id, URI uri) {
		return new HandlerDiscoverer.Result(HandlerFunctions.forward(uri.getPath()), Collections.emptyList());
	}

	public static HandlerDiscoverer.Result http(RouteProperties routeProperties) {
		return http(routeProperties.getId(), routeProperties.getUri());
	}

	public static HandlerDiscoverer.Result http(String id, URI uri) {
		HandlerFunction<ServerResponse> http = HandlerFunctions.http();
		return getResult(id, uri, http);
	}

	public static HandlerDiscoverer.Result https(RouteProperties routeProperties) {
		return https(routeProperties.getId(), routeProperties.getUri());
	}

	public static HandlerDiscoverer.Result https(String id, URI uri) {
		return getResult(id, uri, HandlerFunctions.https());
	}

	public static HandlerDiscoverer.Result no(RouteProperties routeProperties) {
		return no(routeProperties.getId(), routeProperties.getUri());
	}

	public static HandlerDiscoverer.Result no(String id, URI uri) {
		return getResult(id, uri, HandlerFunctions.no());
	}

	// for properties
	public static HandlerDiscoverer.Result stream(RouteProperties routeProperties) {
		// stream:bindingName
		return stream(routeProperties.getUri().getSchemeSpecificPart());
	}

	public static HandlerDiscoverer.Result stream(String bindingName) {
		return new HandlerDiscoverer.Result(HandlerFunctions.stream(bindingName), Collections.emptyList(),
				Collections.emptyList());
	}

	private static HandlerDiscoverer.Result getResult(String id, URI uri,
			HandlerFunction<ServerResponse> handlerFunction) {
		HandlerFilterFunction<ServerResponse, ServerResponse> setId = setIdFilter(id);
		HandlerFilterFunction<ServerResponse, ServerResponse> setRequest = setRequestUrlFilter(uri);
		return new HandlerDiscoverer.Result(handlerFunction, Arrays.asList(setId, setRequest), Collections.emptyList());
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
