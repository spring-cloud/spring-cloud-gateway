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

package org.springframework.cloud.gateway.server.mvc;

import java.net.URI;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.context.ApplicationContext;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.function.HandlerFunction;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;
import org.springframework.web.servlet.support.RequestContextUtils;

public abstract class HandlerFunctions {

	protected HandlerFunctions() {

	}

	// TODO: current discovery only goes by method name
	// so last one wins, so put parameterless last
	public static HandlerFunction<ServerResponse> http(URI uri) {
		return new LookupProxyExchangeHandlerFunction(uri);
	}

	public static HandlerFunction<ServerResponse> https(URI uri) {
		return new LookupProxyExchangeHandlerFunction(uri);
	}

	public static HandlerFunction<ServerResponse> https() {
		return http();
	}

	public static HandlerFunction<ServerResponse> http() {
		return new LookupProxyExchangeHandlerFunction();
	}

	public static ApplicationContext getApplicationContext(ServerRequest request) {
		WebApplicationContext webApplicationContext = RequestContextUtils
				.findWebApplicationContext(request.servletRequest());
		if (webApplicationContext == null) {
			throw new IllegalStateException("No Application Context in request attributes");
		}
		return webApplicationContext;
	}

	static class LookupProxyExchangeHandlerFunction implements HandlerFunction<ServerResponse> {

		private final URI uri;

		private AtomicReference<ProxyExchangeHandlerFunction> proxyExchangeHandlerFunction = new AtomicReference<>();

		LookupProxyExchangeHandlerFunction() {
			this.uri = null;
		}

		LookupProxyExchangeHandlerFunction(URI uri) {
			this.uri = uri;
		}

		@Override
		public ServerResponse handle(ServerRequest serverRequest) {
			if (uri != null) {
				// TODO: in 2 places now, here and
				// GatewayMvcPropertiesBeanDefinitionRegistrar
				serverRequest.attributes().put("routeUri", uri);
			}
			this.proxyExchangeHandlerFunction.compareAndSet(null, lookup(serverRequest));
			return proxyExchangeHandlerFunction.get().handle(serverRequest);
		}

		private static ProxyExchangeHandlerFunction lookup(ServerRequest request) {
			return getApplicationContext(request).getBean(ProxyExchangeHandlerFunction.class);
		}

	}

}
