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

package org.springframework.cloud.gateway.server.mvc.test;

import java.net.URI;

import org.springframework.cloud.gateway.server.mvc.HandlerFunctions;
import org.springframework.context.ApplicationContext;
import org.springframework.web.servlet.function.HandlerFilterFunction;
import org.springframework.web.servlet.function.HandlerFunction;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;

public class LocalServerPortUriResolver
		implements HandlerFunctions.URIResolver, HandlerFilterFunction<ServerResponse, ServerResponse> {

	@Override
	public URI apply(ServerRequest request) {
		ApplicationContext context = HandlerFunctions.getApplicationContext(request);
		Integer port = context.getEnvironment().getProperty("local.server.port", Integer.class);
		return URI.create("http://localhost:" + port);
	}

	@Override
	public ServerResponse filter(ServerRequest request, HandlerFunction<ServerResponse> next) throws Exception {
		URI uri = apply(request);
		request.attributes().put("routeUri", uri);
		return next.handle(request);
	}

}
