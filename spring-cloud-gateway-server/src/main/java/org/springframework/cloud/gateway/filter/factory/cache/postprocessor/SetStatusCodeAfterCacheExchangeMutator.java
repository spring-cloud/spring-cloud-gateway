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

import org.springframework.cloud.gateway.filter.factory.cache.CachedResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.util.CollectionUtils;
import org.springframework.web.server.ServerWebExchange;

/**
 * It sets HTTP Status Code depending {@literal no-cache}
 * {@link HttpHeaders#CACHE_CONTROL} header.
 *
 * @author Marta Medio
 * @author Ignacio Lozano
 */
public class SetStatusCodeAfterCacheExchangeMutator implements AfterCacheExchangeMutator {

	private static final String NO_CACHE_VALUE = "no-cache";

	@Override
	public void accept(ServerWebExchange exchange, CachedResponse cachedResponse) {
		HttpHeaders requestHeaders = exchange.getRequest().getHeaders();
		ServerHttpResponse response = exchange.getResponse();

		if (!CollectionUtils.isEmpty(cachedResponse.body()) && isRequestNoCache(requestHeaders)) {
			response.setStatusCode(HttpStatus.NOT_MODIFIED);
		}
		else {
			response.setStatusCode(cachedResponse.statusCode());
		}
	}

	private boolean isRequestNoCache(HttpHeaders requestHeaders) {
		return requestHeaders.getCacheControl() != null && requestHeaders.getCacheControl().contains(NO_CACHE_VALUE);
	}

}
