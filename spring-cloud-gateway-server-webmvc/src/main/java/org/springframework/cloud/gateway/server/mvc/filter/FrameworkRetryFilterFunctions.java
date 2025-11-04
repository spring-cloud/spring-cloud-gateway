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

package org.springframework.cloud.gateway.server.mvc.filter;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

import org.springframework.cloud.gateway.server.mvc.common.Configurable;
import org.springframework.cloud.gateway.server.mvc.common.MvcUtils;
import org.springframework.cloud.gateway.server.mvc.common.Shortcut;
import org.springframework.core.NestedRuntimeException;
import org.springframework.core.retry.RetryPolicy;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.servlet.function.HandlerFilterFunction;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;

/**
 * Retry filter based on retry functionality in Spring Framework.
 *
 * @author Ryan Baxter
 */
public abstract class FrameworkRetryFilterFunctions {

	private FrameworkRetryFilterFunctions() {
	}

	@Shortcut
	public static HandlerFilterFunction<ServerResponse, ServerResponse> frameworkRetry(int retries) {
		return frameworkRetry(config -> config.setRetries(retries));
	}

	public static HandlerFilterFunction<ServerResponse, ServerResponse> frameworkRetry(
			Consumer<RetryConfig> configConsumer) {
		RetryConfig config = new RetryConfig();
		configConsumer.accept(config);
		return frameworkRetry(config);
	}

	@Shortcut({ "retries", "series", "methods" })
	@Configurable
	public static HandlerFilterFunction<ServerResponse, ServerResponse> frameworkRetry(RetryConfig config) {
		return (request, next) -> {
			CompositeRetryPolicy compositeRetryPolicy = new CompositeRetryPolicy(config);

			RetryTemplate retryTemplate = new RetryTemplate();
			retryTemplate.setRetryPolicy(compositeRetryPolicy);

			return retryTemplate.execute(() -> {
				if (config.isCacheBody()) {
					MvcUtils.getOrCacheBody(request);
				}
				reset(request);
				ServerResponse serverResponse = next.handle(request);

				if (isRetryableStatusCode(serverResponse.statusCode(), config)
						&& isRetryableMethod(request.method(), config)) {
					// use this to transfer information for HTTP status retry logic
					throw new RetryException(request, serverResponse);
				}
				return serverResponse;
			});
		};
	}

	private static void reset(ServerRequest request) throws IOException {
		ClientHttpResponse clientHttpResponse = MvcUtils.getAttribute(request, MvcUtils.CLIENT_RESPONSE_ATTR);
		if (clientHttpResponse != null) {
			clientHttpResponse.close();
			MvcUtils.putAttribute(request, MvcUtils.CLIENT_RESPONSE_ATTR, null);
		}
	}

	private static boolean isRetryableStatusCode(HttpStatusCode httpStatus, RetryConfig config) {
		return config.getSeries().stream().anyMatch(series -> HttpStatus.Series.resolve(httpStatus.value()) == series);
	}

	private static boolean isRetryableMethod(HttpMethod method, RetryConfig config) {
		return config.getMethods().contains(method);
	}

	/**
	 * Composite retry policy that combines exception-based and HTTP status-based retry
	 * logic. Each instance is used for a single request execution, so we can use a
	 * regular instance variable instead of ThreadLocal.
	 */
	private static class CompositeRetryPolicy implements RetryPolicy {

		private final RetryConfig config;

		private int attemptCount = 0;

		CompositeRetryPolicy(RetryConfig config) {
			this.config = config;
		}

		@Override
		public boolean shouldRetry(Throwable throwable) {
			// If no throwable, don't retry
			if (throwable == null) {
				return false;
			}

			// Check if we've exceeded max attempts
			// Note: config.getRetries() represents max attempts (including initial
			// attempt)
			// attemptCount tracks the number of attempts made so far (0 = first attempt,
			// 1 = second attempt, etc.)
			// shouldRetry is called after each failed attempt, so:
			// - First failure: attemptCount = 0, we can retry (will become attempt 1)
			// - Second failure: attemptCount = 1, we can retry (will become attempt 2)
			// - Third failure: attemptCount = 2, we can retry (will become attempt 3)
			// - After third failure: attemptCount = 3, we've reached max attempts, stop
			// So if retries=3, we allow attempts 0, 1, 2 (3 total attempts)
			if (attemptCount >= config.getRetries()) {
				return false;
			}

			boolean shouldRetry = false;

			// Check if it's an HTTP status retry case
			if (throwable instanceof RetryException retryException) {
				shouldRetry = isRetryableStatusCode(retryException.getResponse().statusCode(), config)
						&& isRetryableMethod(retryException.getRequest().method(), config);
			}
			else {
				// Check exception-based retry
				shouldRetry = config.getExceptions()
					.stream()
					.anyMatch(exceptionClass -> exceptionClass.isInstance(throwable));
			}

			// If we should retry based on exception/status, increment counter
			// The check above ensures we won't exceed max attempts
			if (shouldRetry) {
				attemptCount++;
				return true;
			}

			return false;
		}

	}

	public static class RetryConfig {

		private int retries = 3;

		private Set<HttpStatus.Series> series = new HashSet<>(List.of(HttpStatus.Series.SERVER_ERROR));

		private Set<Class<? extends Throwable>> exceptions = new HashSet<>(
				List.of(IOException.class, TimeoutException.class, RetryException.class));

		private Set<HttpMethod> methods = new HashSet<>(List.of(HttpMethod.GET));

		private boolean cacheBody = false;

		public int getRetries() {
			return retries;
		}

		public RetryConfig setRetries(int retries) {
			this.retries = retries;
			return this;
		}

		public Set<HttpStatus.Series> getSeries() {
			return series;
		}

		public RetryConfig setSeries(Set<HttpStatus.Series> series) {
			this.series = series;
			return this;
		}

		public RetryConfig addSeries(HttpStatus.Series... series) {
			this.series.addAll(Arrays.asList(series));
			return this;
		}

		public Set<Class<? extends Throwable>> getExceptions() {
			return exceptions;
		}

		public RetryConfig setExceptions(Set<Class<? extends Throwable>> exceptions) {
			this.exceptions = exceptions;
			return this;
		}

		public RetryConfig addExceptions(Class<? extends Throwable>... exceptions) {
			this.exceptions.addAll(Arrays.asList(exceptions));
			return this;
		}

		public Set<HttpMethod> getMethods() {
			return methods;
		}

		public RetryConfig setMethods(Set<HttpMethod> methods) {
			this.methods = methods;
			return this;
		}

		public RetryConfig addMethods(HttpMethod... methods) {
			this.methods.addAll(Arrays.asList(methods));
			return this;
		}

		public boolean isCacheBody() {
			return cacheBody;
		}

		public RetryConfig setCacheBody(boolean cacheBody) {
			this.cacheBody = cacheBody;
			return this;
		}

	}

	static class RetryException extends NestedRuntimeException {

		private final ServerRequest request;

		private final ServerResponse response;

		RetryException(ServerRequest request, ServerResponse response) {
			super(null);
			this.request = request;
			this.response = response;
		}

		public ServerRequest getRequest() {
			return request;
		}

		public ServerResponse getResponse() {
			return response;
		}

	}

	public static class FilterSupplier extends SimpleFilterSupplier {

		public FilterSupplier() {
			super(FrameworkRetryFilterFunctions.class);
		}

	}

}
