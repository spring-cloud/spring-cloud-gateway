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

import org.springframework.web.servlet.function.HandlerFilterFunction;

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
