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
import java.time.Duration;
import java.util.function.Consumer;

import org.springframework.cloud.gateway.server.mvc.common.MvcUtils;
import org.springframework.cloud.gateway.server.mvc.filter.RetryFilterFunctions.BackoffConfig;
import org.springframework.core.retry.RetryPolicy;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.util.backoff.BackOff;
import org.springframework.util.backoff.ExponentialBackOff;
import org.springframework.web.servlet.function.HandlerFilterFunction;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;

/**
 * @author Ryan Baxter
 * @deprecated Once Spring Retry is no longer being maintained, we will remove this class
 * and move the logic to @link GatewayRetryFilterFunctions. Retry filter based on retry
 * functionality in Spring Framework.
 */
@Deprecated
public abstract class FrameworkRetryFilterFunctions {

	private FrameworkRetryFilterFunctions() {
	}

	public static HandlerFilterFunction<ServerResponse, ServerResponse> frameworkRetry(int retries) {
		return frameworkRetry(config -> config.setRetries(retries));
	}

	public static HandlerFilterFunction<ServerResponse, ServerResponse> frameworkRetry(
			Consumer<RetryFilterFunctions.RetryConfig> configConsumer) {
		RetryFilterFunctions.RetryConfig config = new RetryFilterFunctions.RetryConfig();
		configConsumer.accept(config);
		return frameworkRetry(config);
	}

	public static HandlerFilterFunction<ServerResponse, ServerResponse> frameworkRetry(
			RetryFilterFunctions.RetryConfig config) {
		validate(config);
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
					throw new RetryFilterFunctions.RetryException(request, serverResponse);
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

	private static boolean isRetryableStatusCode(HttpStatusCode httpStatus, RetryFilterFunctions.RetryConfig config) {
		return config.getSeries().stream().anyMatch(series -> HttpStatus.Series.resolve(httpStatus.value()) == series);
	}

	private static boolean isRetryableMethod(HttpMethod method, RetryFilterFunctions.RetryConfig config) {
		return config.getMethods().contains(method);
	}

	private static void validate(RetryFilterFunctions.RetryConfig config) {
		if (config.getBackoff() != null) {
			config.getBackoff().validate();
		}
	}

	private static BackOff createBackOff(BackoffConfig backoff) {
		ExponentialBackOff exponentialBackOff = new ExponentialBackOff(backoff.getFirstBackoff().toMillis(),
				backoff.getFactor());
		Duration maxBackoff = backoff.getMaxBackoff();
		if (maxBackoff != null) {
			exponentialBackOff.setMaxInterval(maxBackoff.toMillis());
		}
		return exponentialBackOff;
	}

	/**
	 * Composite retry policy that combines exception-based and HTTP status-based retry
	 * logic. Each instance is used for a single request execution, so we can use a
	 * regular instance variable instead of ThreadLocal.
	 */
	private static class CompositeRetryPolicy implements RetryPolicy {

		private final RetryFilterFunctions.RetryConfig config;

		private int attemptCount = 0;

		private final BackOff backOff;

		CompositeRetryPolicy(RetryFilterFunctions.RetryConfig config) {
			this.config = config;
			this.backOff = config.getBackoff() != null ? createBackOff(config.getBackoff())
					: RetryPolicy.withDefaults().getBackOff();
		}

		@Override
		public BackOff getBackOff() {
			return this.backOff;
		}

		@Override
		public boolean shouldRetry(Throwable throwable) {
			// If no throwable, don't retry
			if (throwable == null) {
				return false;
			}

			// config.getRetries() is the number of retries after the initial attempt.
			// attemptCount counts the retries granted so far and shouldRetry is called
			// after each failed attempt, so retries=3 allows 4 attempts in total.
			if (attemptCount >= config.getRetries()) {
				return false;
			}

			boolean shouldRetry = false;

			// Check if it's an HTTP status retry case
			if (throwable instanceof RetryFilterFunctions.RetryException retryException) {
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

}
