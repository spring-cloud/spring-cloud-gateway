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

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.cloud.gateway.server.mvc.common.Configurable;
import org.springframework.cloud.gateway.server.mvc.common.Shortcut;
import org.springframework.core.NestedRuntimeException;
import org.springframework.core.log.LogMessage;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.util.ClassUtils;
import org.springframework.web.servlet.function.HandlerFilterFunction;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;

public abstract class RetryFilterFunctions {

	private static final Log log = LogFactory.getLog(RetryFilterFunctions.class);

	private static final boolean USE_SPRING_RETRY = ClassUtils
		.isPresent("org.springframework.retry.annotation.Retryable", ClassUtils.getDefaultClassLoader());

	private static boolean useFrameworkRetry = false;

	private RetryFilterFunctions() {
	}

	@Shortcut
	public static HandlerFilterFunction<ServerResponse, ServerResponse> retry(int retries) {
		return useSpringRetry() ? GatewayRetryFilterFunctions.retry(retries)
				: FrameworkRetryFilterFunctions.frameworkRetry(retries);
	}

	public static HandlerFilterFunction<ServerResponse, ServerResponse> retry(Consumer<RetryConfig> configConsumer) {
		return useSpringRetry() ? GatewayRetryFilterFunctions.retry(configConsumer)
				: FrameworkRetryFilterFunctions.frameworkRetry(configConsumer);
	}

	@Shortcut({ "retries", "series", "methods" })
	@Configurable
	public static HandlerFilterFunction<ServerResponse, ServerResponse> retry(RetryConfig config) {
		return useSpringRetry() ? GatewayRetryFilterFunctions.retry(config)
				: FrameworkRetryFilterFunctions.frameworkRetry(config);
	}

	static void setUseFrameworkRetry(boolean useFrameworkRetry) {
		RetryFilterFunctions.useFrameworkRetry = useFrameworkRetry;
	}

	/**
	 * If spring retry is on the classpath and we do not force the use of Framework retry
	 * then we will use Spring Retry.
	 */
	private static boolean useSpringRetry() {
		boolean useSpringRetry = USE_SPRING_RETRY && !useFrameworkRetry;
		if (log.isDebugEnabled()) {
			log.debug(LogMessage.format(
					"Retry filter selection: Spring Retry on classpath=%s, useFrameworkRetry=%s, selected filter=%s",
					USE_SPRING_RETRY, useFrameworkRetry,
					useSpringRetry ? "GatewayRetryFilterFunctions" : "FrameworkRetryFilterFunctions"));
		}
		return useSpringRetry;
	}

	public static class FilterSupplier extends SimpleFilterSupplier {

		public FilterSupplier() {
			super(RetryFilterFunctions.class);
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

	public static class RetryException extends NestedRuntimeException {

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

}
