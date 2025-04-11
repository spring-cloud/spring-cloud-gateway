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

import java.net.URI;
import java.util.function.Consumer;

import org.springframework.cloud.gateway.server.mvc.common.HttpStatusHolder;
import org.springframework.cloud.gateway.server.mvc.common.KeyValues;
import org.springframework.cloud.gateway.server.mvc.common.Shortcut;
import org.springframework.cloud.gateway.server.mvc.filter.AfterFilterFunctions.DedupeStrategy;
import org.springframework.cloud.gateway.server.mvc.filter.BeforeFilterFunctions.FallbackHeadersConfig;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.util.Assert;
import org.springframework.util.unit.DataSize;
import org.springframework.web.servlet.function.HandlerFilterFunction;
import org.springframework.web.servlet.function.ServerResponse;

import static org.springframework.web.servlet.function.HandlerFilterFunction.ofRequestProcessor;
import static org.springframework.web.servlet.function.HandlerFilterFunction.ofResponseProcessor;

// TODO: can Bucket4j, CircuitBreaker, Retry be here and not cause CNFE?
public interface FilterFunctions {

	@Shortcut
	static HandlerFilterFunction<ServerResponse, ServerResponse> adaptCachedBody() {
		return ofRequestProcessor(BeforeFilterFunctions.adaptCachedBody());
	}

	@Shortcut
	static HandlerFilterFunction<ServerResponse, ServerResponse> addRequestHeader(String name, String... values) {
		return ofRequestProcessor(BeforeFilterFunctions.addRequestHeader(name, values));
	}

	static HandlerFilterFunction<ServerResponse, ServerResponse> addRequestHeadersIfNotPresent(String values) {
		return addRequestHeadersIfNotPresent(KeyValues.valueOf(values));
	}

	@Shortcut
	static HandlerFilterFunction<ServerResponse, ServerResponse> addRequestHeadersIfNotPresent(KeyValues keyValues) {
		return ofRequestProcessor(BeforeFilterFunctions.addRequestHeadersIfNotPresent(keyValues.getKeyValues()));
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
	static HandlerFilterFunction<ServerResponse, ServerResponse> dedupeResponseHeader(String name) {
		return ofResponseProcessor(AfterFilterFunctions.dedupeResponseHeader(name));
	}

	@Shortcut
	static HandlerFilterFunction<ServerResponse, ServerResponse> dedupeResponseHeader(String name,
			DedupeStrategy strategy) {
		return ofResponseProcessor(AfterFilterFunctions.dedupeResponseHeader(name, strategy));
	}

	@Shortcut
	static HandlerFilterFunction<ServerResponse, ServerResponse> fallbackHeaders() {
		return ofRequestProcessor(BeforeFilterFunctions.fallbackHeaders());
	}

	static HandlerFilterFunction<ServerResponse, ServerResponse> fallbackHeaders(
			Consumer<FallbackHeadersConfig> configConsumer) {
		return ofRequestProcessor(BeforeFilterFunctions.fallbackHeaders(configConsumer));
	}

	@Shortcut
	static HandlerFilterFunction<ServerResponse, ServerResponse> mapRequestHeader(String fromHeader, String toHeader) {
		return ofRequestProcessor(BeforeFilterFunctions.mapRequestHeader(fromHeader, toHeader));
	}

	@Shortcut
	static HandlerFilterFunction<ServerResponse, ServerResponse> prefixPath(String prefix) {
		return ofRequestProcessor(BeforeFilterFunctions.prefixPath(prefix));
	}

	@Shortcut
	static HandlerFilterFunction<ServerResponse, ServerResponse> preserveHost() {
		return ofRequestProcessor(BeforeFilterFunctions.preserveHostHeader());
	}

	static HandlerFilterFunction<ServerResponse, ServerResponse> redirectTo(int status, URI uri) {
		return redirectTo(new HttpStatusHolder(null, status), uri);
	}

	static HandlerFilterFunction<ServerResponse, ServerResponse> redirectTo(String status, URI uri) {
		return redirectTo(HttpStatusHolder.valueOf(status), uri);
	}

	static HandlerFilterFunction<ServerResponse, ServerResponse> redirectTo(HttpStatusCode status, URI uri) {
		return redirectTo(new HttpStatusHolder(status, null), uri);
	}

	@Shortcut
	static HandlerFilterFunction<ServerResponse, ServerResponse> redirectTo(HttpStatusHolder status, URI uri) {
		Assert.isTrue(status.is3xxRedirection(), "status must be a 3xx code, but was " + status);

		return (request,
				next) -> ServerResponse.status(status.resolve()).header(HttpHeaders.LOCATION, uri.toString()).build();
	}

	@Shortcut
	static HandlerFilterFunction<ServerResponse, ServerResponse> removeRequestHeader(String name) {
		return ofRequestProcessor(BeforeFilterFunctions.removeRequestHeader(name));
	}

	@Shortcut
	static HandlerFilterFunction<ServerResponse, ServerResponse> removeRequestParameter(String name) {
		return ofRequestProcessor(BeforeFilterFunctions.removeRequestParameter(name));
	}

	@Shortcut
	static HandlerFilterFunction<ServerResponse, ServerResponse> removeResponseHeader(String name) {
		return ofResponseProcessor(AfterFilterFunctions.removeResponseHeader(name));
	}

	@Shortcut
	static HandlerFilterFunction<ServerResponse, ServerResponse> requestHeaderSize(String maxSize) {
		return ofRequestProcessor(BeforeFilterFunctions.requestHeaderSize(maxSize));
	}

	static HandlerFilterFunction<ServerResponse, ServerResponse> requestHeaderSize(DataSize maxSize) {
		return ofRequestProcessor(BeforeFilterFunctions.requestHeaderSize(maxSize));
	}

	@Shortcut
	static HandlerFilterFunction<ServerResponse, ServerResponse> requestHeaderToRequestUri(String name) {
		return ofRequestProcessor(BeforeFilterFunctions.requestHeaderToRequestUri(name));
	}

	@Shortcut
	static HandlerFilterFunction<ServerResponse, ServerResponse> requestSize(String maxSize) {
		return ofRequestProcessor(BeforeFilterFunctions.requestSize(maxSize));
	}

	static HandlerFilterFunction<ServerResponse, ServerResponse> requestSize(DataSize maxSize) {
		return ofRequestProcessor(BeforeFilterFunctions.requestSize(maxSize));
	}

	@Shortcut
	static HandlerFilterFunction<ServerResponse, ServerResponse> rewriteLocationResponseHeader(String stripVersion,
			String locationHeaderName, String hostValue, String protocolsRegex) {
		return ofResponseProcessor(RewriteLocationResponseHeaderFilterFunctions
			.rewriteLocationResponseHeader(config -> config.setStripVersion(stripVersion)
				.setLocationHeaderName(locationHeaderName)
				.setHostValue(hostValue)
				.setProtocolsRegex(protocolsRegex)));
	}

	@Shortcut
	static HandlerFilterFunction<ServerResponse, ServerResponse> rewritePath(String regexp, String replacement) {
		return ofRequestProcessor(BeforeFilterFunctions.rewritePath(regexp, replacement));
	}

	@Shortcut
	static HandlerFilterFunction<ServerResponse, ServerResponse> rewriteResponseHeader(String name, String regexp,
			String replacement) {
		return ofResponseProcessor(AfterFilterFunctions.rewriteResponseHeader(name, regexp, replacement));
	}

	@Shortcut
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

	static HandlerFilterFunction<ServerResponse, ServerResponse> uri(String uri) {
		return ofRequestProcessor(BeforeFilterFunctions.uri(uri));
	}

	@Shortcut
	static HandlerFilterFunction<ServerResponse, ServerResponse> uri(URI uri) {
		return ofRequestProcessor(BeforeFilterFunctions.uri(uri));
	}

	class FilterSupplier extends SimpleFilterSupplier {

		public FilterSupplier() {
			super(FilterFunctions.class);
		}

	}

}
