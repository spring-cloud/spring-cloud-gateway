/*
 * Copyright 2013-2018 the original author or authors.
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

package org.springframework.cloud.gateway.filter.headers;

import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;

import java.util.List;

@FunctionalInterface
public interface HttpHeadersFilter {

	HttpHeaders filter(ServerHttpRequest request);

	static HttpHeaders filter(List<HttpHeadersFilter> filters, ServerHttpRequest request) {
		HttpHeaders response = request.getHeaders();
		if (filters != null) {
			for (HttpHeadersFilter filter: filters) {
				HttpHeaders filtered = filter.filter(request);
				request = request.mutate().headers(httpHeaders -> {
					httpHeaders.putAll(filtered);
				}).build();
				response = filtered;
			}
		}
		return response;
	}
}
