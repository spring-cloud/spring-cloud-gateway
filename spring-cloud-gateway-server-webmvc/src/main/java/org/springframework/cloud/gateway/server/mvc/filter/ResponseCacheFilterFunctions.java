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

import java.time.Duration;

import org.springframework.util.unit.DataSize;
import org.springframework.web.servlet.function.HandlerFilterFunction;
import org.springframework.web.servlet.function.HandlerFunction;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;

/**
 * The central interface that enables the caching of certain HTTP responses, thereby
 * reducing latency and overhead for the upstream server.
 * <p>
 * I'm currently keeping all feature-specific code in this class. Whether and how the code
 * should be moved to separate classes and packages can be decided later.
 *
 * @author Ingo Griebsch
 */
public abstract class ResponseCacheFilterFunctions {

	private ResponseCacheFilterFunctions() {
	}

	public static HandlerFilterFunction<ServerResponse, ServerResponse> responseCache(Duration timeToLive,
			DataSize cacheSize) {
		return new ResponseCacheFilter();
	}

	/**
	 * A {@link HandlerFilterFunction} implementation that allows to cache certain HTTP
	 * responses.
	 *
	 * @author Ingo Griebsch
	 */
	static class ResponseCacheFilter implements HandlerFilterFunction<ServerResponse, ServerResponse> {

		@Override
		public ServerResponse filter(ServerRequest request, HandlerFunction<ServerResponse> next) throws Exception {
			// FIXME implement me...

			// If the request is not cacheable, simple continue with the next filter.

			// FIXME Should we remember that this filter is now applied?

			// If the request should be revalidated continue with the request an put the
			// response in the cache.

			// Check if a response for this request is already cached.
			// If not, continue with the request an put the response in the cache.
			// If so, return the cached response but update the metatdata built based on
			// the request.

			return next.handle(request);
		}

	}

	/**
	 * A {@link FilterSupplier} implementation that provides all
	 * {@link HandlerFilterFunction handler filter functions} available to be able to
	 * cache HTTP responses.
	 *
	 * @author Ingo Griebsch
	 */
	static class FilterSupplier extends SimpleFilterSupplier {

		FilterSupplier() {
			super(ResponseCacheFilterFunctions.class);
		}

	}

}
