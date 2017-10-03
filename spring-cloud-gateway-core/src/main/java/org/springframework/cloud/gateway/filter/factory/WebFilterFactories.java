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
import org.springframework.web.server.WebFilter;

import java.net.URI;
import java.util.Arrays;

import static org.springframework.cloud.gateway.filter.factory.RedirectToWebFilterFactory.STATUS_KEY;
import static org.springframework.cloud.gateway.filter.factory.RedirectToWebFilterFactory.URL_KEY;
import static org.springframework.cloud.gateway.filter.factory.RewritePathWebFilterFactory.REGEXP_KEY;
import static org.springframework.cloud.gateway.filter.factory.RewritePathWebFilterFactory.REPLACEMENT_KEY;
import static org.springframework.cloud.gateway.filter.factory.WebFilterFactory.NAME_KEY;
import static org.springframework.cloud.gateway.filter.factory.WebFilterFactory.VALUE_KEY;
import static org.springframework.tuple.TupleBuilder.tuple;

/**
 * @author Spencer Gibb
 */
public class WebFilterFactories {

	public static final Tuple EMPTY_TUPLE = tuple().build();

	public static WebFilter addRequestHeader(String headerName, String headerValue) {
		Tuple args = tuple().of(NAME_KEY, headerName, VALUE_KEY, headerValue);
		return new AddRequestHeaderWebFilterFactory().apply(args);
	}

	public static WebFilter addRequestParameter(String param, String value) {
		Tuple args = tuple().of(NAME_KEY, param, VALUE_KEY, value);
		return new AddRequestParameterWebFilterFactory().apply(args);
	}

	public static WebFilter addResponseHeader(String headerName, String headerValue) {
		Tuple args = tuple().of(NAME_KEY, headerName, VALUE_KEY, headerValue);
		return new AddResponseHeaderWebFilterFactory().apply(args);
	}

	public static WebFilter hystrix(String commandName) {
		Tuple args = tuple().of(NAME_KEY, commandName);
		return new HystrixWebFilterFactory().apply(args);
	}

	public static WebFilter prefixPath(String prefix) {
		Tuple args = tuple().of(PrefixPathWebFilterFactory.PREFIX_KEY, prefix);
		return new PrefixPathWebFilterFactory().apply(args);
	}

	public static WebFilter redirect(int status, URI url) {
		return redirect(String.valueOf(status), url.toString());
	}

	public static WebFilter redirect(int status, String url) {
		return redirect(String.valueOf(status), url);
	}

	public static WebFilter redirect(String status, URI url) {
		return redirect(status, url.toString());
	}

	public static WebFilter redirect(String status, String url) {
		Tuple args = tuple().of(STATUS_KEY, status, URL_KEY, url);
		return new RedirectToWebFilterFactory().apply(args);
	}

	public static WebFilter removeNonProxyHeaders(String... headersToRemove) {
		RemoveNonProxyHeadersWebFilterFactory filterFactory = new RemoveNonProxyHeadersWebFilterFactory();
		filterFactory.setHeaders(Arrays.asList(headersToRemove));
		return filterFactory.apply(EMPTY_TUPLE);
	}

	public static WebFilter removeRequestHeader(String headerName) {
		Tuple args = tuple().of(NAME_KEY, headerName);
		return new RemoveRequestHeaderWebFilterFactory().apply(args);
	}

	public static WebFilter removeResponseHeader(String headerName) {
		Tuple args = tuple().of(NAME_KEY, headerName);
		return new RemoveResponseHeaderWebFilterFactory().apply(args);
	}

	public static WebFilter rewritePath(String regex, String replacement) {
		Tuple args = tuple().of(REGEXP_KEY, regex, REPLACEMENT_KEY, replacement);
		return new RewritePathWebFilterFactory().apply(args);
	}

	public static WebFilter secureHeaders(SecureHeadersProperties properties) {
		return new SecureHeadersWebFilterFactory(properties).apply(EMPTY_TUPLE);
	}

	public static WebFilter setPath(String template) {
		Tuple args = tuple().of(SetPathWebFilterFactory.TEMPLATE_KEY, template);
		return new SetPathWebFilterFactory().apply(args);
	}

	public static WebFilter setResponseHeader(String headerName, String headerValue) {
		Tuple args = tuple().of(NAME_KEY, headerName, VALUE_KEY, headerValue);
		return new SetResponseHeaderWebFilterFactory().apply(args);
	}

	public static WebFilter setStatus(int status) {
		return setStatus(String.valueOf(status));
	}

	public static WebFilter setStatus(String status) {
		Tuple args = tuple().of(SetStatusWebFilterFactory.STATUS_KEY, status);
		return new SetStatusWebFilterFactory().apply(args);
	}
}
