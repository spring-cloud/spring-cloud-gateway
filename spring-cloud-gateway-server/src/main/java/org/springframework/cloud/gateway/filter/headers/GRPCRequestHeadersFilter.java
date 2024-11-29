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

import java.util.List;
import java.util.Map;

import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;

/**
 * @author Alberto C. Ríos
 */
public class GRPCRequestHeadersFilter implements HttpHeadersFilter, Ordered {

	@Override
	public HttpHeaders filter(HttpHeaders headers, ServerWebExchange exchange) {
		HttpHeaders updated = new HttpHeaders();

		for (Map.Entry<String, List<String>> entry : headers.headerSet()) {
			updated.addAll(entry.getKey(), entry.getValue());
		}

		// https://datatracker.ietf.org/doc/html/rfc7540#section-8.1.2.2
		if (isGRPC(headers.getFirst(HttpHeaders.CONTENT_TYPE))) {
			updated.add("te", "trailers");
		}
		return updated;
	}

	private boolean isGRPC(String contentTypeValue) {
		return StringUtils.startsWithIgnoreCase(contentTypeValue, "application/grpc");
	}

	@Override
	public boolean supports(Type type) {
		return Type.REQUEST.equals(type);
	}

	@Override
	public int getOrder() {
		return Ordered.LOWEST_PRECEDENCE;
	}

}
