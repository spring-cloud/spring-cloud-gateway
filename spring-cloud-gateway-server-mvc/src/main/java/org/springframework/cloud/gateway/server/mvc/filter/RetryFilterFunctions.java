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

import org.springframework.cloud.gateway.server.mvc.common.Configurable;
import org.springframework.cloud.gateway.server.mvc.common.MvcUtils;
import org.springframework.cloud.gateway.server.mvc.common.Shortcut;
import org.springframework.core.NestedRuntimeException;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.retry.RetryContext;
import org.springframework.retry.RetryPolicy;
import org.springframework.retry.policy.CompositeRetryPolicy;
import org.springframework.retry.policy.NeverRetryPolicy;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.retry.support.RetryTemplateBuilder;
import org.springframework.web.servlet.function.HandlerFilterFunction;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;

public abstract class RetryFilterFunctions {

	private RetryFilterFunctions() {
	}

	@Shortcut
	public static HandlerFilterFunction<ServerResponse, ServerResponse> retry(int retries) {
		return retry(config -> config.setRetries(retries));
	}

	public static HandlerFilterFunction<ServerResponse, ServerResponse> retry(Consumer<RetryConfig> configConsumer) {
		RetryConfig config = new RetryConfig();
		configConsumer.accept(config);
		return retry(config);
	}

	@Shortcut({ "retries", "series", "methods" })
	@Configurable
	public static HandlerFilterFunction<ServerResponse, ServerResponse> retry(RetryConfig config) {
		RetryTemplateBuilder retryTemplateBuilder = RetryTemplate.builder();
		CompositeRetryPolicy compositeRetryPolicy = new CompositeRetryPolicy();
		Map<Class<? extends Throwable>, Boolean> retryableExceptions = new HashMap<>();
		config.getExceptions().forEach(exception -> retryableExceptions.put(exception, true));
		SimpleRetryPolicy simpleRetryPolicy = new SimpleRetryPolicy(config.getRetries(), retryableExceptions);
		compositeRetryPolicy
			.setPolicies(Arrays.asList(simpleRetryPolicy, new HttpRetryPolicy(config)).toArray(new RetryPolicy[0]));
		RetryTemplate retryTemplate = retryTemplateBuilder.customPolicy(compositeRetryPolicy).build();
		return (request, next) -> retryTemplate.execute(context -> {
			if (config.isCacheBody()) {
				MvcUtils.getOrCacheBody(request);
			}
			ServerResponse serverResponse = next.handle(request);

			if (isRetryableStatusCode(serverResponse.statusCode(), config)
					&& isRetryableMethod(request.method(), config)) {
				// use this to transfer information to HttpStatusRetryPolicy
				throw new RetryException(request, serverResponse);
			}
			return serverResponse;
		});
	}

	private static boolean isRetryableStatusCode(HttpStatusCode httpStatus, RetryConfig config) {
		return config.getSeries().stream().anyMatch(series -> HttpStatus.Series.resolve(httpStatus.value()) == series);
	}

	private static boolean isRetryableMethod(HttpMethod method, RetryConfig config) {
		return config.methods.contains(method);
	}

	public static class HttpRetryPolicy extends NeverRetryPolicy {

		private final RetryConfig config;

		public HttpRetryPolicy(RetryConfig config) {
			this.config = config;
		}

		@Override
		public boolean canRetry(RetryContext context) {
			// TODO: custom exception
			if (context.getLastThrowable() instanceof RetryException e) {
				return isRetryableStatusCode(e.getResponse().statusCode(), config)
						&& isRetryableMethod(e.getRequest().method(), config);
			}
			return super.canRetry(context);
		}

	}

	public static class RetryConfig {

		private int retries = 3;

		private Set<HttpStatus.Series> series = new HashSet<>(List.of(HttpStatus.Series.SERVER_ERROR));

		private Set<Class<? extends Throwable>> exceptions = new HashSet<>(
				List.of(IOException.class, TimeoutException.class, RetryException.class));

		private Set<HttpMethod> methods = new HashSet<>(List.of(HttpMethod.GET));

		private boolean cacheBody = false;

		// TODO: individual statuses
		// TODO: backoff
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

	private static class RetryException extends NestedRuntimeException {

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
			super(RetryFilterFunctions.class);
		}

	}

}
