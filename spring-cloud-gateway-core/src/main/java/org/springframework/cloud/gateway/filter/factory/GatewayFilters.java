/*
 * Copyright 2013-2017 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package org.springframework.cloud.gateway.filter.factory;

import java.net.URI;
import java.net.URL;
import java.util.Arrays;

import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.http.HttpStatus;
import org.springframework.tuple.Tuple;

import com.netflix.hystrix.HystrixObservableCommand;

import static org.springframework.tuple.TupleBuilder.tuple;

/**
 * @deprecated inject {@link org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder} bean instead
 * @author Spencer Gibb
 */
@Deprecated
public class GatewayFilters {

	public static final Tuple EMPTY_TUPLE = tuple().build();

	public static GatewayFilter addRequestHeader(String headerName, String headerValue) {
		return new AddRequestHeaderGatewayFilterFactory().apply(headerName, headerValue);
	}

	public static GatewayFilter addRequestParameter(String param, String value) {
		return new AddRequestParameterGatewayFilterFactory().apply(param, value);
	}

	public static GatewayFilter addResponseHeader(String headerName, String headerValue) {
		return new AddResponseHeaderGatewayFilterFactory().apply(headerName, headerValue);
	}

	public static GatewayFilter hystrix(String commandName) {
		return new HystrixGatewayFilterFactory().apply(commandName);
	}

	public static GatewayFilter hystrix(HystrixObservableCommand.Setter setter) {
		return new HystrixGatewayFilterFactory().apply(setter);
	}

	public static GatewayFilter prefixPath(String prefix) {
		return new PrefixPathGatewayFilterFactory().apply(prefix);
	}

	public static GatewayFilter redirect(int status, URI url) {
		return redirect(String.valueOf(status), url.toString());
	}

	public static GatewayFilter redirect(int status, String url) {
		return redirect(String.valueOf(status), url);
	}

	public static GatewayFilter redirect(String status, URI url) {
		return redirect(status, url.toString());
	}

	public static GatewayFilter redirect(String status, String url) {
		return new RedirectToGatewayFilterFactory().apply(status, url);
	}

	public static GatewayFilter redirect(HttpStatus status, URL url) {
		return new RedirectToGatewayFilterFactory().apply(status, url);
	}

	public static GatewayFilter removeNonProxyHeaders(String... headersToRemove) {
		RemoveNonProxyHeadersGatewayFilterFactory filterFactory = new RemoveNonProxyHeadersGatewayFilterFactory();
		filterFactory.setHeaders(Arrays.asList(headersToRemove));
		return filterFactory.apply(EMPTY_TUPLE);
	}

	public static GatewayFilter removeRequestHeader(String headerName) {
		return new RemoveRequestHeaderGatewayFilterFactory().apply(headerName);
	}

	public static GatewayFilter removeResponseHeader(String headerName) {
		return new RemoveResponseHeaderGatewayFilterFactory().apply(headerName);
	}

	public static GatewayFilter rewritePath(String regex, String replacement) {
		return new RewritePathGatewayFilterFactory().apply(regex, replacement);
	}

	public static GatewayFilter secureHeaders(SecureHeadersProperties properties) {
		return new SecureHeadersGatewayFilterFactory(properties).apply(EMPTY_TUPLE);
	}

	public static GatewayFilter setPath(String template) {
		return new SetPathGatewayFilterFactory().apply(template);
	}

	public static GatewayFilter setResponseHeader(String headerName, String headerValue) {
		return new SetResponseHeaderGatewayFilterFactory().apply(headerName, headerValue);
	}

	public static GatewayFilter setStatus(int status) {
		return setStatus(String.valueOf(status));
	}

	public static GatewayFilter setStatus(String status) {
		return new SetStatusGatewayFilterFactory().apply(status);
	}

	public static GatewayFilter setStatus(HttpStatus status) {
		return new SetStatusGatewayFilterFactory().apply(status);
	}
}
