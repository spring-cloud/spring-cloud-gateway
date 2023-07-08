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

package org.springframework.cloud.gateway.server.mvc.filter;

import java.util.Arrays;
import java.util.function.BiFunction;

import org.springframework.cloud.gateway.server.mvc.common.HttpStatusHolder;
import org.springframework.cloud.gateway.server.mvc.common.MvcUtils;
import org.springframework.cloud.gateway.server.mvc.handler.GatewayServerResponse;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;

public interface AfterFilterFunctions {

	static BiFunction<ServerRequest, ServerResponse, ServerResponse> addResponseHeader(String name, String... values) {
		return (request, response) -> {
			if (response instanceof GatewayServerResponse res) {
				String[] expandedValues = MvcUtils.expandMultiple(request, values);
				res.headers().addAll(name, Arrays.asList(expandedValues));
			}
			return response;
		};
	}

	static BiFunction<ServerRequest, ServerResponse, ServerResponse> setResponseHeader(String name, String value) {
		return (request, response) -> {
			if (response instanceof GatewayServerResponse res) {
				String expandedValue = MvcUtils.expand(request, value);
				res.headers().set(name, expandedValue);
			}
			return response;
		};
	}

	static BiFunction<ServerRequest, ServerResponse, ServerResponse> setStatus(int statusCode) {
		return setStatus(new HttpStatusHolder(null, statusCode));
	}

	static BiFunction<ServerRequest, ServerResponse, ServerResponse> setStatus(HttpStatusCode statusCode) {
		return setStatus(new HttpStatusHolder(statusCode, null));
	}

	static BiFunction<ServerRequest, ServerResponse, ServerResponse> setStatus(HttpStatusHolder statusCode) {
		return (request, response) -> {
			if (response instanceof GatewayServerResponse res) {
				if (statusCode.getStatus() != null) {
					res.setStatusCode(HttpStatusCode.valueOf(statusCode.getStatus()));
				}
				else {
					res.setStatusCode(statusCode.getHttpStatus());
				}
			}
			return response;
		};
	}

}
