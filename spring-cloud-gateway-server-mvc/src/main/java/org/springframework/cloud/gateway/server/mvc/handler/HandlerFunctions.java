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
import java.lang.reflect.Method;
import java.net.URI;
import java.util.Arrays;
import java.util.Collection;
import java.util.concurrent.atomic.AtomicReference;

import jakarta.servlet.ServletException;

import org.springframework.cloud.gateway.server.mvc.common.MvcUtils;
import org.springframework.web.servlet.function.HandlerFunction;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;

public abstract class HandlerFunctions {

	private HandlerFunctions() {

	}

	public static HandlerFunction<ServerResponse> forward(String path) {
		// ok() is wrong, but can be overridden by the forwarded request.
		return request -> GatewayServerResponse.ok().build((httpServletRequest, httpServletResponse) -> {
			try {
				String expandedFallback = MvcUtils.expand(request, path);
				request.servletRequest().getServletContext().getRequestDispatcher(expandedFallback)
						.forward(httpServletRequest, httpServletResponse);
				return null;
			}
			catch (ServletException | IOException e) {
				throw new RuntimeException(e);
			}
		});
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
				serverRequest.attributes().put(MvcUtils.GATEWAY_REQUEST_URL_ATTR, uri);
			}
			this.proxyExchangeHandlerFunction.compareAndSet(null, lookup(serverRequest));
			return proxyExchangeHandlerFunction.get().handle(serverRequest);
		}

		private static ProxyExchangeHandlerFunction lookup(ServerRequest request) {
			return MvcUtils.getApplicationContext(request).getBean(ProxyExchangeHandlerFunction.class);
		}

		@Override
		public String toString() {
			ProxyExchangeHandlerFunction handlerFunction = this.proxyExchangeHandlerFunction.get();
			if (handlerFunction != null) {
				return handlerFunction.toString();
			}
			return ProxyExchangeHandlerFunction.class.getSimpleName();
		}

	}

	public static class HandlerSupplier
			implements org.springframework.cloud.gateway.server.mvc.handler.HandlerSupplier {

		@Override
		public Collection<Method> get() {
			return Arrays.asList(HandlerFunctions.class.getMethods());
		}

	}

}
