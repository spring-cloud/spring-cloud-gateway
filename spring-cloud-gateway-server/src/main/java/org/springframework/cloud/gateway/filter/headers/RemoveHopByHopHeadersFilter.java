/*
 * Copyright 2013-2020 the original author or authors.
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

package org.springframework.cloud.gateway.filter.headers;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.util.Assert;
import org.springframework.web.server.ServerWebExchange;

@ConfigurationProperties("spring.cloud.gateway.filter.remove-hop-by-hop")
public class RemoveHopByHopHeadersFilter implements HttpHeadersFilter, Ordered {

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

	public Set<String> getHeaders() {
		return headers;
	}

	public void setHeaders(Set<String> headers) {
		Assert.notNull(headers, "headers may not be null");
		this.headers = headers.stream().map(String::toLowerCase).collect(Collectors.toSet());
	}

	@Override
	public int getOrder() {
		return order;
	}

	public void setOrder(int order) {
		this.order = order;
	}

	@Override
	public HttpHeaders filter(HttpHeaders originalHeaders, ServerWebExchange exchange) {
		HttpHeaders filtered = new HttpHeaders();
		List<String> connectionOptions = originalHeaders.getConnection().stream().map(String::toLowerCase).toList();
		Set<String> headersToRemove = new HashSet<>(headers);
		headersToRemove.addAll(connectionOptions);

		for (Map.Entry<String, List<String>> entry : originalHeaders.entrySet()) {
			if (!headersToRemove.contains(entry.getKey().toLowerCase())) {
				filtered.addAll(entry.getKey(), entry.getValue());
			}
		}

		return filtered;
	}

	@Override
	public boolean supports(Type type) {
		return type.equals(Type.REQUEST) || type.equals(Type.RESPONSE);
	}

}
