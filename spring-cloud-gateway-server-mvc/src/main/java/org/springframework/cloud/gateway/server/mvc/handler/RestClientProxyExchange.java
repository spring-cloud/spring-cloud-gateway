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

import java.io.IOException;
import java.lang.reflect.Field;

import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.util.ClassUtils;
import org.springframework.util.ReflectionUtils;
import org.springframework.util.StreamUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.servlet.function.ServerResponse;

public class RestClientProxyExchange implements ProxyExchange {

	private final RestClient restClient;

	private final Field clientResponseField;

	public RestClientProxyExchange(ClientHttpRequestFactory requestFactory) {
		restClient = RestClient.builder().requestFactory(requestFactory).build();
		try {
			clientResponseField = ReflectionUtils.findField(
					ClassUtils.forName("org.springframework.web.client.DefaultRestClient$DefaultResponseSpec", null),
					"clientResponse");
			ReflectionUtils.makeAccessible(clientResponseField);
		}
		catch (ClassNotFoundException e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public ServerResponse exchange(Request request) {
		// return restClient.method(request.getMethod()).uri(request.getUri())
		RestClient.ResponseSpec responseSpec = restClient.method(request.getMethod()).uri(request.getUri())
				.headers(httpHeaders -> {
					request.getHttpHeaders().forEach((header, values) -> {
						// TODO: why does this help form encoding?
						if (!header.equalsIgnoreCase("content-length")) {
							httpHeaders.put(header, values);
						}
					});
				}).body(outputStream -> StreamUtils.copy(request.getServerRequest().servletRequest().getInputStream(),
						outputStream))
				.retrieve();
		ClientHttpResponse clientHttpResponse = (ClientHttpResponse) ReflectionUtils.getField(clientResponseField,
				responseSpec);
		try {
			ServerResponse serverResponse = GatewayServerResponse.status(clientHttpResponse.getStatusCode())
					.build((req, httpServletResponse) -> {
						try (clientHttpResponse) {
							// copy body from request to clientHttpRequest
							StreamUtils.copy(clientHttpResponse.getBody(), httpServletResponse.getOutputStream());
						}
						catch (IOException e) {
							throw new RuntimeException(e);
						}
						return null;
					});
			serverResponse.headers()
					.putAll(request.getResponseHeadersFilter().apply(clientHttpResponse.getHeaders(), serverResponse));
			return serverResponse;
		}
		catch (IOException e) {
			throw new RuntimeException(e);
		}
		// @formatter:off
		/*.exchange((clientRequest, clientResponse) -> {
			ServerResponse serverResponse = GatewayServerResponse.status(clientResponse.getStatusCode())
					.build((req, httpServletResponse) -> {
						try {
							// copy body from request to clientHttpRequest
							StreamUtils.copy(clientResponse.getBody(), httpServletResponse.getOutputStream());
						}
						catch (IOException e) {
							throw new RuntimeException(e);
						}
						return null;
					});
			serverResponse.headers()
					.putAll(request.getResponseHeadersFilter().apply(clientResponse.getHeaders(), serverResponse));
			return serverResponse;
		});*/
		// @formatter:on
	}

}
