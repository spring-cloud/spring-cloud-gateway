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

import org.springframework.core.Ordered;
import org.springframework.web.servlet.function.HandlerFunction;
import org.springframework.web.servlet.function.ServerRequest;

/**
 * Filter that gets executed in the request phase.
 * The filter wil be executed after the {@link HandlerFunction} lowPrecedence filters and the route's own request-filters (like rewritePath).
 * Order can be defined through the {@link Ordered#getOrder()} method, this will have precedence over the bean @Order.
 * Lower values will match first.
 * @author Joris Oosterhuis
 */
public interface GlobalRequestFilter extends Ordered {

	ServerRequest processRequest(ServerRequest request);

	@Override
	default int getOrder() {
		return 0;
	}
}
