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
import java.io.InputStream;
import java.io.OutputStream;

import org.springframework.cloud.gateway.server.mvc.common.MvcUtils;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.util.StreamUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.servlet.function.ServerResponse;

public class RestClientProxyExchange implements ProxyExchange {

	private final RestClient restClient;

	public RestClientProxyExchange(RestClient restClient) {
		this.restClient = restClient;
	}

	@Override
	public ServerResponse exchange(Request request) {
		return restClient.method(request.getMethod()).uri(request.getUri())
				.headers(httpHeaders -> httpHeaders.putAll(request.getHeaders()))
				.body(outputStream -> copyBody(request, outputStream))
				.exchange((clientRequest, clientResponse) -> doExchange(request, clientResponse), false);
	}

	private static int copyBody(Request request, OutputStream outputStream) throws IOException {
		return StreamUtils.copy(request.getServerRequest().servletRequest().getInputStream(), outputStream);
	}

	private static ServerResponse doExchange(Request request, ClientHttpResponse clientResponse) throws IOException {
		InputStream body = clientResponse.getBody();
		// put the body input stream in a request attribute so filters can read it.
		MvcUtils.putAttribute(request.getServerRequest(), MvcUtils.CLIENT_RESPONSE_INPUT_STREAM_ATTR, body);
		ServerResponse serverResponse = GatewayServerResponse.status(clientResponse.getStatusCode())
				.build((req, httpServletResponse) -> {
					try (clientResponse) {
						// get input stream from request attribute in case it was
						// modified.
						InputStream inputStream = MvcUtils.getAttribute(request.getServerRequest(),
								MvcUtils.CLIENT_RESPONSE_INPUT_STREAM_ATTR);
						// copy body from request to clientHttpRequest
						StreamUtils.copy(inputStream, httpServletResponse.getOutputStream());
					}
					return null;
				});
		ClientHttpResponseAdapter proxyExchangeResponse = new ClientHttpResponseAdapter(clientResponse);
		request.getResponseConsumers()
				.forEach(responseConsumer -> responseConsumer.accept(proxyExchangeResponse, serverResponse));
		return serverResponse;
	}

}
