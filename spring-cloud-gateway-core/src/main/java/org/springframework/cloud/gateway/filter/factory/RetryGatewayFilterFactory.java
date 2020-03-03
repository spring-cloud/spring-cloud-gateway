/*
 * Copyright 2013-2019 the original author or authors.
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

package org.springframework.cloud.gateway.filter.factory;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.Supplier;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Mono;
import reactor.retry.Backoff;
import reactor.retry.Repeat;
import reactor.retry.RepeatContext;
import reactor.retry.Retry;
import reactor.retry.RetryContext;

import org.springframework.cloud.gateway.event.EnableBodyCachingEvent;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.support.HasRouteId;
import org.springframework.cloud.gateway.support.TimeoutException;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatus.Series;
import org.springframework.util.Assert;
import org.springframework.web.server.ServerWebExchange;

import static org.springframework.cloud.gateway.support.GatewayToStringStyler.filterToStringCreator;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.CLIENT_RESPONSE_HEADER_NAMES;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.removeAlreadyRouted;

public class RetryGatewayFilterFactory
		extends AbstractGatewayFilterFactory<RetryGatewayFilterFactory.RetryConfig> {

	/**
	 * Retry iteration key.
	 */
	public static final String RETRY_ITERATION_KEY = "retry_iteration";

	private static final Log log = LogFactory.getLog(RetryGatewayFilterFactory.class);

	public RetryGatewayFilterFactory() {
		super(RetryConfig.class);
	}

	private static <T> List<T> toList(T... items) {
		return new ArrayList<>(Arrays.asList(items));
	}

	@Override
	public GatewayFilter apply(RetryConfig retryConfig) {
		retryConfig.validate();

		Repeat<ServerWebExchange> statusCodeRepeat = null;
		if (!retryConfig.getStatuses().isEmpty() || !retryConfig.getSeries().isEmpty()) {
			Predicate<RepeatContext<ServerWebExchange>> repeatPredicate = context -> {
				ServerWebExchange exchange = context.applicationContext();
				if (exceedsMaxIterations(exchange, retryConfig)) {
					return false;
				}

				HttpStatus statusCode = exchange.getResponse().getStatusCode();

				boolean retryableStatusCode = retryConfig.getStatuses()
						.contains(statusCode);

				if (!retryableStatusCode && statusCode != null) { // null status code
																	// might mean a
																	// network exception?
					// try the series
					retryableStatusCode = retryConfig.getSeries().stream()
							.anyMatch(series -> statusCode.series().equals(series));
				}

				final boolean finalRetryableStatusCode = retryableStatusCode;
				trace("retryableStatusCode: %b, statusCode %s, configured statuses %s, configured series %s",
						() -> finalRetryableStatusCode, () -> statusCode,
						retryConfig::getStatuses, retryConfig::getSeries);

				HttpMethod httpMethod = exchange.getRequest().getMethod();
				boolean retryableMethod = retryConfig.getMethods().contains(httpMethod);

				trace("retryableMethod: %b, httpMethod %s, configured methods %s",
						() -> retryableMethod, () -> httpMethod, retryConfig::getMethods);
				return retryableMethod && finalRetryableStatusCode;
			};

			statusCodeRepeat = Repeat.onlyIf(repeatPredicate)
					.doOnRepeat(context -> reset(context.applicationContext()));

			BackoffConfig backoff = retryConfig.getBackoff();
			if (backoff != null) {
				statusCodeRepeat = statusCodeRepeat.backoff(getBackoff(backoff));
			}
		}

		// TODO: support timeout, backoff, jitter, etc... in Builder

		Retry<ServerWebExchange> exceptionRetry = null;
		if (!retryConfig.getExceptions().isEmpty()) {
			Predicate<RetryContext<ServerWebExchange>> retryContextPredicate = context -> {

				ServerWebExchange exchange = context.applicationContext();

				if (exceedsMaxIterations(exchange, retryConfig)) {
					return false;
				}

				Throwable exception = context.exception();
				for (Class<? extends Throwable> retryableClass : retryConfig
						.getExceptions()) {
					if (retryableClass.isInstance(exception) || (exception != null
							&& retryableClass.isInstance(exception.getCause()))) {
						trace("exception or its cause is retryable %s, configured exceptions %s",
								() -> getExceptionNameWithCause(exception),
								retryConfig::getExceptions);

						HttpMethod httpMethod = exchange.getRequest().getMethod();
						boolean retryableMethod = retryConfig.getMethods()
								.contains(httpMethod);
						trace("retryableMethod: %b, httpMethod %s, configured methods %s",
								() -> retryableMethod, () -> httpMethod,
								retryConfig::getMethods);
						return retryableMethod;
					}
				}
				trace("exception or its cause is not retryable %s, configured exceptions %s",
						() -> getExceptionNameWithCause(exception),
						retryConfig::getExceptions);
				return false;
			};
			exceptionRetry = Retry.onlyIf(retryContextPredicate)
					.doOnRetry(context -> reset(context.applicationContext()))
					.retryMax(retryConfig.getRetries());
			BackoffConfig backoff = retryConfig.getBackoff();
			if (backoff != null) {
				exceptionRetry = exceptionRetry.backoff(getBackoff(backoff));
			}
		}

		GatewayFilter gatewayFilter = apply(retryConfig.getRouteId(), statusCodeRepeat,
				exceptionRetry);
		return new GatewayFilter() {
			@Override
			public Mono<Void> filter(ServerWebExchange exchange,
					GatewayFilterChain chain) {
				return gatewayFilter.filter(exchange, chain);
			}

			@Override
			public String toString() {
				return filterToStringCreator(RetryGatewayFilterFactory.this)
						.append("retries", retryConfig.getRetries())
						.append("series", retryConfig.getSeries())
						.append("statuses", retryConfig.getStatuses())
						.append("methods", retryConfig.getMethods())
						.append("exceptions", retryConfig.getExceptions()).toString();
			}
		};
	}

	private String getExceptionNameWithCause(Throwable exception) {
		if (exception != null) {
			StringBuilder builder = new StringBuilder(exception.getClass().getName());
			Throwable cause = exception.getCause();
			if (cause != null) {
				builder.append("{cause=").append(cause.getClass().getName()).append("}");
			}
			return builder.toString();
		}
		else {
			return "null";
		}
	}

	private Backoff getBackoff(BackoffConfig backoff) {
		return Backoff.exponential(backoff.firstBackoff, backoff.maxBackoff,
				backoff.factor, backoff.basedOnPreviousValue);
	}

	public boolean exceedsMaxIterations(ServerWebExchange exchange,
			RetryConfig retryConfig) {
		Integer iteration = exchange.getAttribute(RETRY_ITERATION_KEY);

		// TODO: deal with null iteration
		boolean exceeds = iteration != null && iteration >= retryConfig.getRetries();
		trace("exceedsMaxIterations %b, iteration %d, configured retries %d",
				() -> exceeds, () -> iteration, retryConfig::getRetries);
		return exceeds;
	}

	public void reset(ServerWebExchange exchange) {
		// TODO: what else to do to reset exchange?
		Set<String> addedHeaders = exchange.getAttributeOrDefault(
				CLIENT_RESPONSE_HEADER_NAMES, Collections.emptySet());
		addedHeaders
				.forEach(header -> exchange.getResponse().getHeaders().remove(header));
		removeAlreadyRouted(exchange);
	}

	public GatewayFilter apply(String routeId, Repeat<ServerWebExchange> repeat,
			Retry<ServerWebExchange> retry) {
		if (routeId != null && getPublisher() != null) {
			// send an event to enable caching
			getPublisher().publishEvent(new EnableBodyCachingEvent(this, routeId));
		}
		return (exchange, chain) -> {
			trace("Entering retry-filter");

			// chain.filter returns a Mono<Void>
			Publisher<Void> publisher = chain.filter(exchange)
					// .log("retry-filter", Level.INFO)
					.doOnSuccessOrError((aVoid, throwable) -> {
						int iteration = exchange
								.getAttributeOrDefault(RETRY_ITERATION_KEY, -1);
						int newIteration = iteration + 1;
						trace("setting new iteration in attr %d", () -> newIteration);
						exchange.getAttributes().put(RETRY_ITERATION_KEY, newIteration);
					});

			if (retry != null) {
				// retryWhen returns a Mono<Void>
				// retry needs to go before repeat
				publisher = ((Mono<Void>) publisher)
						.retryWhen(retry.withApplicationContext(exchange));
			}
			if (repeat != null) {
				// repeatWhen returns a Flux<Void>
				// so this needs to be last and the variable a Publisher<Void>
				publisher = ((Mono<Void>) publisher)
						.repeatWhen(repeat.withApplicationContext(exchange));
			}

			return Mono.fromDirect(publisher);
		};
	}

	@SafeVarargs
	private final void trace(String message, Supplier<Object>... argSuppliers) {
		if (log.isTraceEnabled()) {
			Object[] args = new Object[argSuppliers.length];
			int i = 0;
			for (Supplier<Object> a : argSuppliers) {
				args[i] = a.get();
				++i;
			}
			log.trace(String.format(message, args));
		}
	}

	@SuppressWarnings("unchecked")
	public static class RetryConfig implements HasRouteId {

		private String routeId;

		private int retries = 3;

		private List<Series> series = toList(Series.SERVER_ERROR);

		private List<HttpStatus> statuses = new ArrayList<>();

		private List<HttpMethod> methods = toList(HttpMethod.GET);

		private List<Class<? extends Throwable>> exceptions = toList(IOException.class,
				TimeoutException.class);

		private BackoffConfig backoff;

		public RetryConfig allMethods() {
			return setMethods(HttpMethod.values());
		}

		public void validate() {
			Assert.isTrue(this.retries > 0, "retries must be greater than 0");
			Assert.isTrue(
					!this.series.isEmpty() || !this.statuses.isEmpty()
							|| !this.exceptions.isEmpty(),
					"series, status and exceptions may not all be empty");
			Assert.notEmpty(this.methods, "methods may not be empty");
			if (this.backoff != null) {
				this.backoff.validate();
			}
		}

		public BackoffConfig getBackoff() {
			return backoff;
		}

		public RetryConfig setBackoff(BackoffConfig backoff) {
			this.backoff = backoff;
			return this;
		}

		public RetryConfig setBackoff(Duration firstBackoff, Duration maxBackoff,
				int factor, boolean basedOnPreviousValue) {
			this.backoff = new BackoffConfig(firstBackoff, maxBackoff, factor,
					basedOnPreviousValue);
			return this;
		}

		@Override
		public void setRouteId(String routeId) {
			this.routeId = routeId;
		}

		@Override
		public String getRouteId() {
			return this.routeId;
		}

		public int getRetries() {
			return retries;
		}

		public RetryConfig setRetries(int retries) {
			this.retries = retries;
			return this;
		}

		public List<Series> getSeries() {
			return series;
		}

		public RetryConfig setSeries(Series... series) {
			this.series = Arrays.asList(series);
			return this;
		}

		public List<HttpStatus> getStatuses() {
			return statuses;
		}

		public RetryConfig setStatuses(HttpStatus... statuses) {
			this.statuses = Arrays.asList(statuses);
			return this;
		}

		public List<HttpMethod> getMethods() {
			return methods;
		}

		public RetryConfig setMethods(HttpMethod... methods) {
			this.methods = Arrays.asList(methods);
			return this;
		}

		public List<Class<? extends Throwable>> getExceptions() {
			return exceptions;
		}

		public RetryConfig setExceptions(Class<? extends Throwable>... exceptions) {
			this.exceptions = Arrays.asList(exceptions);
			return this;
		}

	}

	public static class BackoffConfig {

		private Duration firstBackoff = Duration.ofMillis(5);

		private Duration maxBackoff;

		private int factor = 2;

		private boolean basedOnPreviousValue = true;

		public BackoffConfig() {
		}

		public BackoffConfig(Duration firstBackoff, Duration maxBackoff, int factor,
				boolean basedOnPreviousValue) {
			this.firstBackoff = firstBackoff;
			this.maxBackoff = maxBackoff;
			this.factor = factor;
			this.basedOnPreviousValue = basedOnPreviousValue;
		}

		public void validate() {
			Assert.notNull(this.firstBackoff, "firstBackoff must be present");
		}

		public Duration getFirstBackoff() {
			return firstBackoff;
		}

		public void setFirstBackoff(Duration firstBackoff) {
			this.firstBackoff = firstBackoff;
		}

		public Duration getMaxBackoff() {
			return maxBackoff;
		}

		public void setMaxBackoff(Duration maxBackoff) {
			this.maxBackoff = maxBackoff;
		}

		public int getFactor() {
			return factor;
		}

		public void setFactor(int factor) {
			this.factor = factor;
		}

		public boolean isBasedOnPreviousValue() {
			return basedOnPreviousValue;
		}

		public void setBasedOnPreviousValue(boolean basedOnPreviousValue) {
			this.basedOnPreviousValue = basedOnPreviousValue;
		}

	}

}
