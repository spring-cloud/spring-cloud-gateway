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

import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.web.servlet.function.ServerRequest;

public class TransferEncodingNormalizationRequestHeadersFilter
		implements HttpHeadersFilter.RequestHttpHeadersFilter, Ordered {

	@Override
	public int getOrder() {
		return 1000;
	}

	@Override
	public HttpHeaders apply(HttpHeaders input, ServerRequest request) {
		String transferEncoding = input.getFirst(HttpHeaders.TRANSFER_ENCODING);
		if (transferEncoding != null && "chunked".equalsIgnoreCase(transferEncoding.trim())
				&& input.containsHeader(HttpHeaders.CONTENT_LENGTH)) {

			HttpHeaders filtered = new HttpHeaders();
			// avoids read only if input is read only
			filtered.addAll(input);
			filtered.remove(HttpHeaders.CONTENT_LENGTH);
			return filtered;
		}
		return input;
	}

}
