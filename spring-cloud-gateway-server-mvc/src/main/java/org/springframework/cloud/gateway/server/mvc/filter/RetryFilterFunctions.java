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
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.retry.RetryContext;
import org.springframework.retry.RetryPolicy;
import org.springframework.retry.policy.CompositeRetryPolicy;
import org.springframework.retry.policy.NeverRetryPolicy;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.retry.support.RetryTemplateBuilder;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.servlet.function.HandlerFilterFunction;
import org.springframework.web.servlet.function.ServerResponse;

public abstract class RetryFilterFunctions {

	private RetryFilterFunctions() {
	}

	public static HandlerFilterFunction<ServerResponse, ServerResponse> retry(int retries) {
		return retry(config -> config.setRetries(retries));
	}

	public static HandlerFilterFunction<ServerResponse, ServerResponse> retry(Consumer<RetryConfig> configConsumer) {
		RetryConfig config = new RetryConfig();
		configConsumer.accept(config);
		RetryTemplateBuilder retryTemplateBuilder = RetryTemplate.builder();
		CompositeRetryPolicy compositeRetryPolicy = new CompositeRetryPolicy();
		Map<Class<? extends Throwable>, Boolean> retryableExceptions = new HashMap<>();
		config.getExceptions().forEach(exception -> retryableExceptions.put(exception, true));
		SimpleRetryPolicy simpleRetryPolicy = new SimpleRetryPolicy(config.getRetries(), retryableExceptions);
		compositeRetryPolicy.setPolicies(
				Arrays.asList(simpleRetryPolicy, new HttpStatusRetryPolicy(config)).toArray(new RetryPolicy[0]));
		RetryTemplate retryTemplate = retryTemplateBuilder.customPolicy(compositeRetryPolicy).build();
		return (request, next) -> retryTemplate.execute(context -> {
			ServerResponse serverResponse = next.handle(request);

			if (isRetryableStatusCode(serverResponse.statusCode(), config)) {
				throw new HttpServerErrorException(serverResponse.statusCode());
			}
			return serverResponse;
		});
	}

	private static boolean isRetryableStatusCode(HttpStatusCode httpStatus, RetryConfig config) {
		return config.getSeries().stream().anyMatch(series -> HttpStatus.Series.resolve(httpStatus.value()) == series);
	}

	public static class HttpStatusRetryPolicy extends NeverRetryPolicy {

		private final RetryConfig config;

		public HttpStatusRetryPolicy(RetryConfig config) {
			this.config = config;
		}

		@Override
		public boolean canRetry(RetryContext context) {
			// TODO: custom exception
			if (context.getLastThrowable() instanceof HttpServerErrorException e) {
				return isRetryableStatusCode(e.getStatusCode(), config);
			}
			return super.canRetry(context);
		}

	}

	public static class RetryConfig {

		private int retries = 3;

		private Set<HttpStatus.Series> series = new HashSet<>(List.of(HttpStatus.Series.SERVER_ERROR));

		private Set<Class<? extends Throwable>> exceptions = new HashSet<>(
				List.of(IOException.class, TimeoutException.class, HttpServerErrorException.class));

		// TODO: individual statuses
		// TODO: support more Spring Retry policies

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

	}

}
