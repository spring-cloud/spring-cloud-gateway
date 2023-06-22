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

import java.io.IOException;
import java.util.concurrent.TimeoutException;

import jakarta.servlet.ServletException;

import org.springframework.cloud.client.circuitbreaker.CircuitBreaker;
import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;
import org.springframework.cloud.gateway.server.mvc.common.MvcUtils;
import org.springframework.cloud.gateway.server.mvc.handler.GatewayServerResponse;
import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.function.HandlerFilterFunction;
import org.springframework.web.servlet.function.ServerResponse;

public abstract class CircuitBreakerFilterFunctions {

	private CircuitBreakerFilterFunctions() {
	}

	public static HandlerFilterFunction<ServerResponse, ServerResponse> circuitBreaker(String id, String fallbackPath) {
		return (request, next) -> {
			CircuitBreakerFactory<?, ?> circuitBreakerFactory = MvcUtils.getApplicationContext(request)
					.getBean(CircuitBreakerFactory.class);
			// TODO: cache
			CircuitBreaker circuitBreaker = circuitBreakerFactory.create(id);
			return circuitBreaker.run(() -> {
				try {
					ServerResponse serverResponse = next.handle(request);
					// TODO: on configured status code, throw exception
					return serverResponse;
				}
				catch (RuntimeException e) {
					throw e;
				}
				catch (Exception e) {
					throw new RuntimeException(e);
				}
			}, throwable -> {
				// if no fallback
				if (!StringUtils.hasText(fallbackPath)) {
					// if timeout exception, GATEWAY_TIMEOUT
					if (throwable instanceof TimeoutException) {
						throw new ResponseStatusException(HttpStatus.GATEWAY_TIMEOUT, throwable.getMessage(),
								throwable);
					}
					// TODO: if not permitted (like circuit open), SERVICE_UNAVAILABLE
					// TODO: if resume without error, return ok response?
					throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, throwable.getMessage(),
							throwable);
				}

				// handle fallback
				return GatewayServerResponse.ok().build((httpServletRequest, httpServletResponse) -> {
					try {
						// TODO: uri template vars support
						request.servletRequest().getServletContext().getRequestDispatcher(fallbackPath)
								.forward(httpServletRequest, httpServletResponse);
						// TODO: what to return for ModelAndView
						return null;
					}
					catch (ServletException | IOException e) {
						throw new RuntimeException(e);
					}
				});
			});
		};
	}

}
