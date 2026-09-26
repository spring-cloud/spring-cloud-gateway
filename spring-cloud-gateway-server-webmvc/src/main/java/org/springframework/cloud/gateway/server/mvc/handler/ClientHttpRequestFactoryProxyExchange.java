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

package org.springframework.cloud.gateway.server.mvc.handler;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.cloud.gateway.server.mvc.common.AbstractProxyExchange;
import org.springframework.cloud.gateway.server.mvc.common.MvcUtils;
import org.springframework.cloud.gateway.server.mvc.config.GatewayMvcProperties;
import org.springframework.http.client.ClientHttpRequest;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.util.StreamUtils;
import org.springframework.web.servlet.function.ServerResponse;

public class ClientHttpRequestFactoryProxyExchange extends AbstractProxyExchange {

	private final ClientHttpRequestFactory requestFactory;

	public ClientHttpRequestFactoryProxyExchange(ClientHttpRequestFactory requestFactory,
			GatewayMvcProperties properties) {
		super(properties);
		this.requestFactory = requestFactory;
	}

	@Override
	public ServerResponse exchange(Request request) {
		AtomicReference<Runnable> releaseResponse = new AtomicReference<>(() -> {
		});
		try {
			Objects.requireNonNull(request.getUri(), "uri is required");
			ClientHttpRequest clientHttpRequest = requestFactory.createRequest(request.getUri(), request.getMethod());
			clientHttpRequest.getHeaders().putAll(request.getHeaders());
			// copy body from request to clientHttpRequest
			StreamUtils.copy(request.getServerRequest().servletRequest().getInputStream(), clientHttpRequest.getBody());
			ClientHttpResponse clientHttpResponse = clientHttpRequest.execute();
			AtomicBoolean closed = new AtomicBoolean();
			releaseResponse.set(() -> {
				if (closed.compareAndSet(false, true)) {
					closeUnwrittenResponse(request, clientHttpResponse);
				}
			});
			InputStream body = clientHttpResponse.getBody();
			// put the body input stream in a request attribute so filters can read it.
			MvcUtils.putAttribute(request.getServerRequest(), MvcUtils.CLIENT_RESPONSE_INPUT_STREAM_ATTR, body);
			MvcUtils.putAttribute(request.getServerRequest(), MvcUtils.CLIENT_RESPONSE_ATTR, clientHttpResponse);
			ServerResponse serverResponse = new GatewayServerResponseBuilder(clientHttpResponse.getStatusCode())
				.build((req, httpServletResponse) -> {
					// get input stream from request attribute in case it was
					// modified.
					InputStream inputStream = MvcUtils.getAttribute(request.getServerRequest(),
							MvcUtils.CLIENT_RESPONSE_INPUT_STREAM_ATTR);
					Objects.requireNonNull(inputStream, "input stream cannot be null");
					IOException copyException = null;
					try {
						// copy body from request to clientHttpRequest
						ClientHttpRequestFactoryProxyExchange.this.copyResponseBody(clientHttpResponse, inputStream,
								httpServletResponse.getOutputStream());
					}
					catch (IOException ex) {
						copyException = ex;
						throw ex;
					}
					finally {
						if (closed.compareAndSet(false, true)) {
							ClientHttpRequestFactoryProxyExchange.this.closeResponse(clientHttpResponse, inputStream,
									copyException);
						}
					}
					return null;
				}, releaseResponse.get());
			ClientHttpResponseAdapter proxyExchangeResponse = new ClientHttpResponseAdapter(clientHttpResponse);
			request.getResponseConsumers()
				.forEach(responseConsumer -> responseConsumer.accept(proxyExchangeResponse, serverResponse));
			return serverResponse;
		}
		catch (IOException e) {
			releaseResponse.get().run();
			// TODO: log error?
			throw new UncheckedIOException(e);
		}
		catch (RuntimeException e) {
			releaseResponse.get().run();
			throw e;
		}
	}

}
