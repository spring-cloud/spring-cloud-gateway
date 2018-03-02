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

package org.springframework.cloud.gateway.fn;

import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;

import static org.springframework.cloud.gateway.fn.FilterFunctions.addRequestHeader;
import static org.springframework.cloud.gateway.fn.HandlerFunctions.http;
import static org.springframework.web.reactive.function.server.RequestPredicates.path;

public class TestRouterFunctionLocator implements RouterFunctionLocator {

	@Override
	public RouterFunction<ServerResponse> locate() {
		return RouterFunctions
				.route(req -> path("/headers").test(req),
						http("http://httpbin.org:80"))
				.filter(addRequestHeader("X-Foo", "Baz"));
	}
}
