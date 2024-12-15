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

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.springframework.cloud.gateway.server.mvc.filter.HttpHeadersFilter.RequestHttpHeadersFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.web.servlet.function.ServerRequest;

public class RemoveHopByHopRequestHeadersFilter implements RequestHttpHeadersFilter, Ordered {

	// TODO: configurable
	/**
	 * Headers to remove as the result of applying the filter.
	 */
	public static final Set<String> HEADERS_REMOVED_ON_REQUEST = new HashSet<>(
			Arrays.asList("connection", "keep-alive", "transfer-encoding", "te", "trailer", "proxy-authorization",
					"proxy-authenticate", "x-application-context", "upgrade"
			// these two are not listed in
			// https://tools.ietf.org/html/draft-ietf-httpbis-p1-messaging-14#section-7.1.3
			// "proxy-connection",
			// "content-length",
			));

	private int order = Ordered.LOWEST_PRECEDENCE - 1;

	private Set<String> headers = HEADERS_REMOVED_ON_REQUEST;

	@Override
	public HttpHeaders apply(HttpHeaders input, ServerRequest serverRequest) {
		return filter(input, headers);
	}

	static HttpHeaders filter(HttpHeaders input, Set<String> headersToRemove) {
		HttpHeaders filtered = new HttpHeaders();

		for (Map.Entry<String, List<String>> entry : input.headerSet()) {
			if (!headersToRemove.contains(entry.getKey().toLowerCase(Locale.ROOT))) {
				filtered.addAll(entry.getKey(), entry.getValue());
			}
		}

		return filtered;
	}

	@Override
	public int getOrder() {
		return order;
	}

}
