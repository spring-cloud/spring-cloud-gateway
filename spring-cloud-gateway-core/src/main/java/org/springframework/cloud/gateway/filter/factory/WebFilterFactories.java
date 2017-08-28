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

import static org.springframework.cloud.gateway.filter.factory.WebFilterFactory.NAME_KEY;
import static org.springframework.cloud.gateway.filter.factory.WebFilterFactory.VALUE_KEY;
import static org.springframework.tuple.TupleBuilder.tuple;

/**
 * @author Spencer Gibb
 */
public class WebFilterFactories {
	//TODO: add support for AddRequestHeaderWebFilterFactory

	//TODO: add support for AddRequestParameterWebFilterFactory

	public static WebFilter addResponseHeader(String headerName, String headerValue) {
		Tuple args = tuple().of(NAME_KEY, headerName, VALUE_KEY, headerValue);
		return new AddResponseHeaderWebFilterFactory().apply(args);
	}

	//TODO: add support for HystrixWebFilterFactory

	//TODO: add support for PrefixPathWebFilterFactory

	//TODO: add support for RedirectToWebFilterFactory

	//TODO: add support for RemoveNonProxyHeadersWebFilterFactory

	//TODO: add support for RemoveRequestHeaderWebFilterFactory

	//TODO: add support for RemoveResponseHeaderWebFilterFactory

	//TODO: add support for RewritePathWebFilterFactory

	//TODO: add support for SecureHeadersProperties

	//TODO: add support for SecureHeadersWebFilterFactory

	//TODO: add support for SetPathWebFilterFactory

	//TODO: add support for SetResponseHeaderWebFilterFactory

	//TODO: add support for SetStatusWebFilterFactory
}
