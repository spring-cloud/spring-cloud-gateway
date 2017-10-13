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

import org.springframework.tuple.Tuple;
import org.springframework.cloud.gateway.filter.GatewayFilter;

import java.net.URI;
import java.util.Arrays;

import static org.springframework.cloud.gateway.filter.factory.RedirectToGatewayFilterFactory.STATUS_KEY;
import static org.springframework.cloud.gateway.filter.factory.RedirectToGatewayFilterFactory.URL_KEY;
import static org.springframework.cloud.gateway.filter.factory.RewritePathGatewayFilterFactory.REGEXP_KEY;
import static org.springframework.cloud.gateway.filter.factory.RewritePathGatewayFilterFactory.REPLACEMENT_KEY;
import static org.springframework.cloud.gateway.filter.factory.GatewayFilterFactory.NAME_KEY;
import static org.springframework.cloud.gateway.filter.factory.GatewayFilterFactory.VALUE_KEY;
import static org.springframework.tuple.TupleBuilder.tuple;

/**
 * @author Spencer Gibb
 */
public class GatewayFilters {

	public static final Tuple EMPTY_TUPLE = tuple().build();

	public static GatewayFilter addRequestHeader(String headerName, String headerValue) {
		Tuple args = tuple().of(NAME_KEY, headerName, VALUE_KEY, headerValue);
		return new AddRequestHeaderGatewayFilterFactory().apply(args);
	}

	public static GatewayFilter addRequestParameter(String param, String value) {
		Tuple args = tuple().of(NAME_KEY, param, VALUE_KEY, value);
		return new AddRequestParameterGatewayFilterFactory().apply(args);
	}

	public static GatewayFilter addResponseHeader(String headerName, String headerValue) {
		Tuple args = tuple().of(NAME_KEY, headerName, VALUE_KEY, headerValue);
		return new AddResponseHeaderGatewayFilterFactory().apply(args);
	}

	public static GatewayFilter hystrix(String commandName) {
		Tuple args = tuple().of(NAME_KEY, commandName);
		return new HystrixGatewayFilterFactory().apply(args);
	}

	public static GatewayFilter prefixPath(String prefix) {
		Tuple args = tuple().of(PrefixPathGatewayFilterFactory.PREFIX_KEY, prefix);
		return new PrefixPathGatewayFilterFactory().apply(args);
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
		Tuple args = tuple().of(STATUS_KEY, status, URL_KEY, url);
		return new RedirectToGatewayFilterFactory().apply(args);
	}

	public static GatewayFilter removeNonProxyHeaders(String... headersToRemove) {
		RemoveNonProxyHeadersGatewayFilterFactory filterFactory = new RemoveNonProxyHeadersGatewayFilterFactory();
		filterFactory.setHeaders(Arrays.asList(headersToRemove));
		return filterFactory.apply(EMPTY_TUPLE);
	}

	public static GatewayFilter removeRequestHeader(String headerName) {
		Tuple args = tuple().of(NAME_KEY, headerName);
		return new RemoveRequestHeaderGatewayFilterFactory().apply(args);
	}

	public static GatewayFilter removeResponseHeader(String headerName) {
		Tuple args = tuple().of(NAME_KEY, headerName);
		return new RemoveResponseHeaderGatewayFilterFactory().apply(args);
	}

	public static GatewayFilter rewritePath(String regex, String replacement) {
		Tuple args = tuple().of(REGEXP_KEY, regex, REPLACEMENT_KEY, replacement);
		return new RewritePathGatewayFilterFactory().apply(args);
	}

	public static GatewayFilter secureHeaders(SecureHeadersProperties properties) {
		return new SecureHeadersGatewayFilterFactory(properties).apply(EMPTY_TUPLE);
	}

	public static GatewayFilter setPath(String template) {
		Tuple args = tuple().of(SetPathGatewayFilterFactory.TEMPLATE_KEY, template);
		return new SetPathGatewayFilterFactory().apply(args);
	}

	public static GatewayFilter setResponseHeader(String headerName, String headerValue) {
		Tuple args = tuple().of(NAME_KEY, headerName, VALUE_KEY, headerValue);
		return new SetResponseHeaderGatewayFilterFactory().apply(args);
	}

	public static GatewayFilter setStatus(int status) {
		return setStatus(String.valueOf(status));
	}

	public static GatewayFilter setStatus(String status) {
		Tuple args = tuple().of(SetStatusGatewayFilterFactory.STATUS_KEY, status);
		return new SetStatusGatewayFilterFactory().apply(args);
	}
}
