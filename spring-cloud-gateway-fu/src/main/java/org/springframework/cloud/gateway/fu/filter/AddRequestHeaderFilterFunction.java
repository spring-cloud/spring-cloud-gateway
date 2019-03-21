/*
 * Copyright 2013-2018 the original author or authors.
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
 *
 */

package org.springframework.cloud.gateway.fu.filter;

import org.springframework.cloud.gateway.fu.MutableHttpHeaders;
import org.springframework.web.reactive.function.server.HandlerFilterFunction;
import org.springframework.web.reactive.function.server.HandlerFunction;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.reactive.function.server.support.ServerRequestWrapper;
import reactor.core.publisher.Mono;

import java.util.Arrays;

class AddRequestHeaderFilterFunction implements HandlerFilterFunction<ServerResponse, ServerResponse> {
	private final String name;
	private final String[] values;

	public AddRequestHeaderFilterFunction(String name, String... values) {
		this.name = name;
		this.values = values;
	}

	@Override
	public Mono<ServerResponse> filter(ServerRequest request, HandlerFunction<ServerResponse> next) {
		MutableHttpHeaders headers = new MutableHttpHeaders(request.headers().asHttpHeaders());
		headers.asHttpHeaders().put(name, Arrays.asList(values));
		ServerRequestWrapper wrapper = new ServerRequestWrapper(request) {
			@Override
			public Headers headers() {
				return headers;
			}
		};
		return next.handle(wrapper);
	}
}
