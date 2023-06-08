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

import java.util.Set;

import org.springframework.cloud.gateway.server.mvc.filter.HttpHeadersFilter.ResponseHttpHeadersFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.web.servlet.function.ServerResponse;

public class RemoveHopByHopResponseHeadersFilter implements ResponseHttpHeadersFilter, Ordered {

	// TODO: configurable

	private int order = Ordered.LOWEST_PRECEDENCE - 1;

	private Set<String> headers = RemoveHopByHopRequestHeadersFilter.HEADERS_REMOVED_ON_REQUEST;

	@Override
	public HttpHeaders apply(HttpHeaders input, ServerResponse serverResponse) {
		return RemoveHopByHopRequestHeadersFilter.filter(input, headers);
	}

	@Override
	public int getOrder() {
		return order;
	}

}
