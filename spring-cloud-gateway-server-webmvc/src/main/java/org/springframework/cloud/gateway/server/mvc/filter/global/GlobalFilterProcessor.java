/*
 * Copyright 2026-present the original author or authors.
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

package org.springframework.cloud.gateway.server.mvc.filter.global;

import java.util.List;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.servlet.function.HandlerFilterFunction;
import org.springframework.web.servlet.function.ServerResponse;

import static org.springframework.web.servlet.function.HandlerFilterFunction.ofRequestProcessor;
import static org.springframework.web.servlet.function.HandlerFilterFunction.ofResponseProcessor;

/**
 * Process the {@link GlobalRequestFilter}s and @{@link GlobalResponseFilter}s into HandlerFilterFunctions to be used in the RouterFunction.
 * @author Joris Oosterhuis
 */
public class GlobalFilterProcessor {

	private final List<HandlerFilterFunction<ServerResponse, ServerResponse>> requestFilters;
	private final List<HandlerFilterFunction<ServerResponse, ServerResponse>> responseFilters;

	public GlobalFilterProcessor(ObjectProvider<GlobalRequestFilter> requestFilterObjectProvider, ObjectProvider<GlobalResponseFilter> responseFilterObjectProvider) {
		this.requestFilters = requestFilterObjectProvider.orderedStream()
				.map(globalRequestFilter -> ofRequestProcessor(globalRequestFilter::processRequest))
				.toList();
		this.responseFilters = responseFilterObjectProvider.orderedStream()
				.map(globalResponseFilter -> ofResponseProcessor(globalResponseFilter::processResponse))
				.toList();
	}

	public List<HandlerFilterFunction<ServerResponse, ServerResponse>> getRequestFilters() {
		return requestFilters;
	}

	public List<HandlerFilterFunction<ServerResponse, ServerResponse>> getResponseFilters() {
		return responseFilters;
	}
}
