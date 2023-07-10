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

package org.springframework.cloud.gateway.server.mvc.filter;

import java.lang.reflect.Method;
import java.net.URI;
import java.util.Arrays;
import java.util.Collection;

import org.springframework.cloud.gateway.server.mvc.common.HttpStatusHolder;
import org.springframework.cloud.gateway.server.mvc.common.Shortcut;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.util.Assert;
import org.springframework.web.servlet.function.HandlerFilterFunction;
import org.springframework.web.servlet.function.ServerResponse;

import static org.springframework.web.servlet.function.HandlerFilterFunction.ofRequestProcessor;
import static org.springframework.web.servlet.function.HandlerFilterFunction.ofResponseProcessor;

public interface FilterFunctions {

	@Shortcut
	static HandlerFilterFunction<ServerResponse, ServerResponse> addRequestHeader(String name, String... values) {
		return ofRequestProcessor(BeforeFilterFunctions.addRequestHeader(name, values));
	}

	@Shortcut
	static HandlerFilterFunction<ServerResponse, ServerResponse> addRequestParameter(String name, String... values) {
		return ofRequestProcessor(BeforeFilterFunctions.addRequestParameter(name, values));
	}

	@Shortcut
	static HandlerFilterFunction<ServerResponse, ServerResponse> addResponseHeader(String name, String... values) {
		return ofResponseProcessor(AfterFilterFunctions.addResponseHeader(name, values));
	}

	@Shortcut
	static HandlerFilterFunction<ServerResponse, ServerResponse> prefixPath(String prefix) {
		return ofRequestProcessor(BeforeFilterFunctions.prefixPath(prefix));
	}

	@Shortcut
	static HandlerFilterFunction<ServerResponse, ServerResponse> preserveHost() {
		return ofRequestProcessor(BeforeFilterFunctions.preserveHost());
	}

	static HandlerFilterFunction<ServerResponse, ServerResponse> redirectTo(int status, URI uri) {
		return redirectTo(new HttpStatusHolder(null, status), uri);
	}

	static HandlerFilterFunction<ServerResponse, ServerResponse> redirectTo(HttpStatusCode status, URI uri) {
		return redirectTo(new HttpStatusHolder(status, null), uri);
	}

	@Shortcut
	static HandlerFilterFunction<ServerResponse, ServerResponse> redirectTo(HttpStatusHolder status, URI uri) {
		Assert.isTrue(status.is3xxRedirection(), "status must be a 3xx code, but was " + status);

		return (request, next) -> ServerResponse.status(status.resolve()).header(HttpHeaders.LOCATION, uri.toString())
				.build();
	}

	static HandlerFilterFunction<ServerResponse, ServerResponse> removeRequestHeader(String name) {
		return ofRequestProcessor(BeforeFilterFunctions.removeRequestHeader(name));
	}

	@Shortcut
	static HandlerFilterFunction<ServerResponse, ServerResponse> rewritePath(String regexp, String replacement) {
		return ofRequestProcessor(BeforeFilterFunctions.rewritePath(regexp, replacement));
	}

	static HandlerFilterFunction<ServerResponse, ServerResponse> routeId(String routeId) {
		return ofRequestProcessor(BeforeFilterFunctions.routeId(routeId));
	}

	@Shortcut
	static HandlerFilterFunction<ServerResponse, ServerResponse> setPath(String path) {
		return ofRequestProcessor(BeforeFilterFunctions.setPath(path));
	}

	@Shortcut
	static HandlerFilterFunction<ServerResponse, ServerResponse> setRequestHeader(String name, String value) {
		return ofRequestProcessor(BeforeFilterFunctions.setRequestHeader(name, value));
	}

	@Shortcut
	static HandlerFilterFunction<ServerResponse, ServerResponse> setRequestHostHeader(String host) {
		return ofRequestProcessor(BeforeFilterFunctions.setRequestHostHeader(host));
	}

	@Shortcut
	static HandlerFilterFunction<ServerResponse, ServerResponse> setResponseHeader(String name, String value) {
		return ofResponseProcessor(AfterFilterFunctions.setResponseHeader(name, value));
	}

	static HandlerFilterFunction<ServerResponse, ServerResponse> stripPrefix() {
		return stripPrefix(1);
	}

	@Shortcut
	static HandlerFilterFunction<ServerResponse, ServerResponse> stripPrefix(int parts) {
		return ofRequestProcessor(BeforeFilterFunctions.stripPrefix(parts));
	}

	static HandlerFilterFunction<ServerResponse, ServerResponse> setStatus(int statusCode) {
		return setStatus(new HttpStatusHolder(null, statusCode));
	}

	static HandlerFilterFunction<ServerResponse, ServerResponse> setStatus(HttpStatusCode statusCode) {
		return setStatus(new HttpStatusHolder(statusCode, null));
	}

	@Shortcut
	static HandlerFilterFunction<ServerResponse, ServerResponse> setStatus(HttpStatusHolder statusCode) {
		return ofResponseProcessor(AfterFilterFunctions.setStatus(statusCode));
	}

	class FilterSupplier implements org.springframework.cloud.gateway.server.mvc.filter.FilterSupplier {

		@Override
		public Collection<Method> get() {
			return Arrays.asList(FilterFunctions.class.getMethods());
		}

	}

}
