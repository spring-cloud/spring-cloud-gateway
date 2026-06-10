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

package org.springframework.cloud.gateway.server.mvc.filter;

import java.util.Locale;

import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.web.servlet.function.ServerRequest;

public class RemoveXForwardedRequestHeadersFilter implements HttpHeadersFilter.RequestHttpHeadersFilter, Ordered {

	@Override
	public int getOrder() {
		return 0;
	}

	@Override
	public HttpHeaders apply(HttpHeaders httpHeaders, ServerRequest serverRequest) {
		return removeXForwardedHeaders(httpHeaders, serverRequest);
	}

	static HttpHeaders removeXForwardedHeaders(HttpHeaders input, ServerRequest serverRequest) {
		return TrustedProxies.filterHeaders(input, serverRequest,
				key -> !key.toLowerCase(Locale.ROOT).startsWith("x-forwarded-"));
	}

}
