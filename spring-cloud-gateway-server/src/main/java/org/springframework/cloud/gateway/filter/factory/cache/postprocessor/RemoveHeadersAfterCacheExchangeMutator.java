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

package org.springframework.cloud.gateway.filter.factory.cache.postprocessor;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.cloud.gateway.filter.factory.cache.CachedResponse;
import org.springframework.web.server.ServerWebExchange;

/**
 * Removes one or more HTTP headers from response. Assumes it is run after
 * {@link SetResponseHeadersAfterCacheExchangeMutator}.
 *
 * @author Abel Salgado Romero
 */
public class RemoveHeadersAfterCacheExchangeMutator implements AfterCacheExchangeMutator {

	private static final Log LOGGER = LogFactory.getLog(RemoveHeadersAfterCacheExchangeMutator.class);

	private final String[] httpHeader;

	public RemoveHeadersAfterCacheExchangeMutator(String... httpHeaders) {
		this.httpHeader = httpHeaders;
	}

	@Override
	public void accept(ServerWebExchange exchange, CachedResponse cachedResponse) {
		for (String header : httpHeader) {
			var previousValue = exchange.getResponse().getHeaders().remove(header);
			if (previousValue != null) {
				LOGGER.debug("HTTP Header value found in response, removing HTTP header " + header);
			}
		}
	}

}
