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
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import jakarta.servlet.ServletException;

import org.springframework.cloud.client.circuitbreaker.CircuitBreaker;
import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;
import org.springframework.cloud.gateway.server.mvc.common.HttpStatusHolder;
import org.springframework.cloud.gateway.server.mvc.common.MvcUtils;
import org.springframework.cloud.gateway.server.mvc.common.Shortcut;
import org.springframework.cloud.gateway.server.mvc.handler.GatewayServerResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.function.HandlerFilterFunction;
import org.springframework.web.servlet.function.ServerResponse;

public abstract class CircuitBreakerFilterFunctions {

	private CircuitBreakerFilterFunctions() {
	}

	@Shortcut
	public static HandlerFilterFunction<ServerResponse, ServerResponse> circuitBreaker(String id) {
		return circuitBreaker(config -> config.setId(id));
	}

	public static HandlerFilterFunction<ServerResponse, ServerResponse> circuitBreaker(String id, String fallbackPath) {
		return circuitBreaker(config -> config.setId(id).setFallbackPath(fallbackPath));
	}

	public static HandlerFilterFunction<ServerResponse, ServerResponse> circuitBreaker(
			Consumer<CircuitBreakerConfig> configConsumer) {
		CircuitBreakerConfig config = new CircuitBreakerConfig();
		configConsumer.accept(config);
		Set<HttpStatusCode> failureStatuses = config.getStatusCodes().stream()
				.map(status -> HttpStatusHolder.valueOf(status).resolve()).collect(Collectors.toSet());
		return (request, next) -> {
			CircuitBreakerFactory<?, ?> circuitBreakerFactory = MvcUtils.getApplicationContext(request)
					.getBean(CircuitBreakerFactory.class);
			// TODO: cache
			CircuitBreaker circuitBreaker = circuitBreakerFactory.create(config.getId());
			return circuitBreaker.run(() -> {
				try {
					ServerResponse serverResponse = next.handle(request);
					// on configured status code, throw exception
					if (failureStatuses.contains(serverResponse.statusCode())) {
						throw new CircuitBreakerStatusCodeException(serverResponse.statusCode());
					}
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
				if (!StringUtils.hasText(config.getFallbackPath())) {
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

				// add the throwable as an attribute. That way, if the fallback is a
				// different gateway route, it can use the fallbackHeaders() filter
				// to convert it to headers.
				request.attributes().put(MvcUtils.CIRCUITBREAKER_EXECUTION_EXCEPTION_ATTR, throwable);

				// handle fallback
				// ok() is wrong, but will be overwritten by the forwarded request
				return GatewayServerResponse.ok().build((httpServletRequest, httpServletResponse) -> {
					try {
						String expandedFallback = MvcUtils.expand(request, config.getFallbackPath());
						request.servletRequest().getServletContext().getRequestDispatcher(expandedFallback)
								.forward(httpServletRequest, httpServletResponse);
						return null;
					}
					catch (ServletException | IOException e) {
						throw new RuntimeException(e);
					}
				});
			});
		};
	}

	public static class CircuitBreakerConfig {

		private String id;

		private String fallbackPath;

		private Set<String> statusCodes = new HashSet<>();

		public String getId() {
			return id;
		}

		public CircuitBreakerConfig setId(String id) {
			this.id = id;
			return this;
		}

		public String getFallbackPath() {
			return fallbackPath;
		}

		public CircuitBreakerConfig setFallbackPath(String fallbackPath) {
			this.fallbackPath = fallbackPath;
			return this;
		}

		public Set<String> getStatusCodes() {
			return statusCodes;
		}

		public CircuitBreakerConfig setStatusCodes(Set<String> statusCodes) {
			this.statusCodes = statusCodes;
			return this;
		}

	}

	public static class CircuitBreakerStatusCodeException extends ResponseStatusException {

		public CircuitBreakerStatusCodeException(HttpStatusCode statusCode) {
			super(statusCode);
		}

	}

	public static class FilterSupplier implements org.springframework.cloud.gateway.server.mvc.filter.FilterSupplier {

		@Override
		public Collection<Method> get() {
			return Arrays.asList(CircuitBreakerFilterFunctions.class.getMethods());
		}

	}

}
