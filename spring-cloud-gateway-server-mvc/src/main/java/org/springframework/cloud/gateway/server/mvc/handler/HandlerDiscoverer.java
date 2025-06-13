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

package org.springframework.cloud.gateway.server.mvc.handler;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

import org.springframework.cloud.gateway.server.mvc.common.AbstractGatewayDiscoverer;
import org.springframework.web.servlet.function.HandlerFilterFunction;
import org.springframework.web.servlet.function.HandlerFunction;
import org.springframework.web.servlet.function.ServerResponse;

public class HandlerDiscoverer extends AbstractGatewayDiscoverer {

	@Override
	public void discover() {
		doDiscover(HandlerSupplier.class, HandlerFunction.class);
		doDiscover(HandlerSupplier.class, HandlerDiscoverer.Result.class);
	}

	public static class Result {

		private final HandlerFunction<ServerResponse> handlerFunction;

		private final List<HandlerFilterFunction<ServerResponse, ServerResponse>> lowerPrecedenceFilters;

		private final List<HandlerFilterFunction<ServerResponse, ServerResponse>> higherPrecedenceFilters;

		public Result(HandlerFunction<ServerResponse> handlerFunction,
				List<HandlerFilterFunction<ServerResponse, ServerResponse>> lowerPrecedenceFilters,
				List<HandlerFilterFunction<ServerResponse, ServerResponse>> higherPrecedenceFilters) {
			this.handlerFunction = handlerFunction;
			this.lowerPrecedenceFilters = Objects.requireNonNullElse(lowerPrecedenceFilters, Collections.emptyList());
			this.higherPrecedenceFilters = Objects.requireNonNullElse(higherPrecedenceFilters, Collections.emptyList());
		}

		public HandlerFunction<ServerResponse> getHandlerFunction() {
			return handlerFunction;
		}

		public List<HandlerFilterFunction<ServerResponse, ServerResponse>> getLowerPrecedenceFilters() {
			return lowerPrecedenceFilters;
		}

		public List<HandlerFilterFunction<ServerResponse, ServerResponse>> getHigherPrecedenceFilters() {
			return higherPrecedenceFilters;
		}

	}

}
