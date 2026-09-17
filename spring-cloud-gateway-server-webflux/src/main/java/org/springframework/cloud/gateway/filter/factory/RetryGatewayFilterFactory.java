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

package org.springframework.cloud.gateway.filter.factory;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jspecify.annotations.Nullable;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.netty.Connection;
import reactor.util.function.Tuples;
import reactor.util.retry.Retry;
import reactor.util.retry.RetryBackoffSpec;
import reactor.util.retry.RetrySpec;

import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.support.HasRouteId;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.cloud.gateway.support.TimeoutException;
import org.springframework.core.style.ToStringCreator;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatus.Series;
import org.springframework.http.HttpStatusCode;
import org.springframework.util.Assert;
import org.springframework.web.server.ServerWebExchange;

import static org.springframework.cloud.gateway.support.GatewayToStringStyler.filterToStringCreator;

public class RetryGatewayFilterFactory extends AbstractGatewayFilterFactory<RetryGatewayFilterFactory.RetryConfig> {

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
	public List<String> shortcutFieldOrder() {
		return Arrays.asList("retries", "statuses", "methods", "backoff.firstBackoff", "backoff.maxBackoff",
				"backoff.factor", "backoff.basedOnPreviousValue", "jitter.randomFactor", "timeout");
	}

	@Override
	public GatewayFilter apply(RetryConfig retryConfig) {
		retryConfig.validate();
		enableBodyCaching(retryConfig.getRouteId());

		boolean hasStatusCodeRepeat = !retryConfig.getStatuses().isEmpty() || !retryConfig.getSeries().isEmpty();
		boolean hasExceptionRetry = !retryConfig.getExceptions().isEmpty();

		GatewayFilter gatewayFilter = (exchange, chain) -> {
			trace("Entering retry-filter");

			// chain.filter returns a Mono<Void>
			Publisher<Void> publisher = chain.filter(exchange)
				// .log("retry-filter", Level.INFO)
				.doOnSuccess(aVoid -> updateIteration(exchange))
				.doOnError(throwable -> updateIteration(exchange));

			if (hasExceptionRetry) {
				// retryWhen returns a Mono<Void>
				// retry needs to go before repeat
				publisher = ((Mono<Void>) publisher).retryWhen(buildExceptionRetry(exchange, retryConfig));
			}
			if (hasStatusCodeRepeat) {
				// repeatWhen returns a Flux<Void>
				// so this needs to be last and the variable a Publisher<Void>
				publisher = ((Mono<Void>) publisher).repeatWhen(buildStatusCodeRepeat(exchange, retryConfig));
			}

			return Mono.fromDirect(publisher);
		};

		return new GatewayFilter() {
			@Override
			public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
				return gatewayFilter.filter(exchange, chain);
			}

			@Override
			public String toString() {
				return filterToStringCreator(RetryGatewayFilterFactory.this).append("routeId", retryConfig.getRouteId())
					.append("retries", retryConfig.getRetries())
					.append("series", retryConfig.getSeries())
					.append("statuses", retryConfig.getStatuses())
					.append("methods", retryConfig.getMethods())
					.append("exceptions", retryConfig.getExceptions())
					.append("backoff", retryConfig.getBackoff())
					.append("jitter", retryConfig.getJitter())
					.append("timeout", retryConfig.getTimeout())
					.toString();
			}
		};
	}

	private boolean isRetryableStatusCode(ServerWebExchange exchange, RetryConfig retryConfig) {
		HttpStatusCode statusCode = exchange.getResponse().getStatusCode();

		boolean retryableStatusCode = retryConfig.getStatuses().contains(statusCode);

		// null status code might mean a network exception?
		if (!retryableStatusCode && statusCode != null) {
			// try the series
			retryableStatusCode = false;
			for (int i = 0; i < retryConfig.getSeries().size(); i++) {
				if (statusCode instanceof HttpStatus) {
					HttpStatus httpStatus = (HttpStatus) statusCode;
					if (httpStatus.series().equals(retryConfig.getSeries().get(i))) {
						retryableStatusCode = true;
						break;
					}
				}
			}
		}

		final boolean finalRetryableStatusCode = retryableStatusCode;
		trace("retryableStatusCode: %b, statusCode %s, configured statuses %s, configured series %s",
				() -> finalRetryableStatusCode, () -> statusCode, retryConfig::getStatuses, retryConfig::getSeries);
		return retryableStatusCode;
	}

	private boolean isRetryableMethod(ServerWebExchange exchange, RetryConfig retryConfig) {
		HttpMethod httpMethod = exchange.getRequest().getMethod();
		boolean retryableMethod = retryConfig.getMethods().contains(httpMethod);

		trace("retryableMethod: %b, httpMethod %s, configured methods %s", () -> retryableMethod, () -> httpMethod,
				retryConfig::getMethods);
		return retryableMethod;
	}

	private boolean withinTimeout(Instant start, @Nullable Duration timeout, Duration upcomingDelay) {
		return timeout == null || Duration.between(start, Instant.now()).plus(upcomingDelay).compareTo(timeout) < 0;
	}

	private Duration computeBackoff(BackoffConfig backoff, long iteration) {
		Duration next = backoff.getFirstBackoff().multipliedBy((long) Math.pow(backoff.getFactor(), iteration - 1));
		Duration max = backoff.getMaxBackoff();
		if (max != null && next.compareTo(max) > 0) {
			return max;
		}
		return next;
	}

	private Duration applyJitter(Duration backoff, JitterConfig jitter) {
		long jitterOffset = (long) (backoff.toMillis() * jitter.getRandomFactor());
		long lowBound = Math.max(backoff.toMillis() - jitterOffset, 0);
		long highBound = backoff.toMillis() + jitterOffset;
		return Duration.ofMillis(ThreadLocalRandom.current().nextLong(lowBound, highBound + 1));
	}

	private Duration nextDelay(long iteration, RetryConfig retryConfig) {
		BackoffConfig backoff = retryConfig.getBackoff();
		if (backoff == null) {
			return Duration.ZERO;
		}
		Duration delay = computeBackoff(backoff, iteration);
		JitterConfig jitter = retryConfig.getJitter();
		if (jitter != null) {
			delay = applyJitter(delay, jitter);
		}
		return delay;
	}

	/**
	 * The next 1-based iteration number, derived from {@link #RETRY_ITERATION_KEY} rather
	 * than the value emitted by {@code repeatWhen}'s companion {@code Flux<Long>} (which
	 * counts items emitted by the source and is always {@code 0} for a {@code Mono<Void>}).
	 */
	private long nextIteration(ServerWebExchange exchange) {
		Integer currentIteration = exchange.getAttribute(RETRY_ITERATION_KEY);
		return (currentIteration == null ? 0 : currentIteration) + 1;
	}

	private Function<Flux<Long>, Publisher<Long>> buildStatusCodeRepeat(ServerWebExchange exchange,
			RetryConfig retryConfig) {
		Instant start = Instant.now();
		// the upcoming delay is computed once per iteration (up front) so the same value can be used both to
		// decide whether it would blow the configured timeout budget, and, if not, as the actual delay applied
		return companion -> companion.map(ignored -> nextIteration(exchange))
			.map(iteration -> Tuples.of(iteration, nextDelay(iteration, retryConfig)))
			.takeWhile(tuple -> !exceedsMaxIterations(exchange, retryConfig)
					&& isRetryableStatusCode(exchange, retryConfig) && isRetryableMethod(exchange, retryConfig)
					&& withinTimeout(start, retryConfig.getTimeout(), tuple.getT2()))
			.doOnNext(tuple -> reset(exchange))
			.concatMap(tuple -> tuple.getT2().isZero() ? Mono.just(tuple.getT1())
					: Mono.delay(tuple.getT2()).thenReturn(tuple.getT1()));
	}

	private Retry buildExceptionRetry(ServerWebExchange exchange, RetryConfig retryConfig) {
		Instant start = Instant.now();
		Predicate<Throwable> predicate = exception -> {
			if (exceedsMaxIterations(exchange, retryConfig) || !withinTimeout(start, retryConfig.getTimeout(),
					nextDelay(nextIteration(exchange), retryConfig))) {
				return false;
			}

			for (Class<? extends Throwable> retryableClass : retryConfig.getExceptions()) {
				if (retryableClass.isInstance(exception)
						|| (exception != null && retryableClass.isInstance(exception.getCause()))) {
					trace("exception or its cause is retryable %s, configured exceptions %s",
							() -> getExceptionNameWithCause(exception), retryConfig::getExceptions);
					return isRetryableMethod(exchange, retryConfig);
				}
			}
			trace("exception or its cause is not retryable %s, configured exceptions %s",
					() -> getExceptionNameWithCause(exception), retryConfig::getExceptions);
			return false;
		};

		BackoffConfig backoff = retryConfig.getBackoff();
		if (backoff != null) {
			RetryBackoffSpec spec = Retry.backoff(retryConfig.getRetries(), backoff.getFirstBackoff())
				.multiplier(backoff.getFactor());
			if (backoff.getMaxBackoff() != null) {
				spec = spec.maxBackoff(backoff.getMaxBackoff());
			}
			JitterConfig jitter = retryConfig.getJitter();
			spec = spec.jitter(jitter != null ? jitter.getRandomFactor() : 0d);
			return spec.filter(predicate)
				.doBeforeRetry(signal -> reset(exchange))
				.onRetryExhaustedThrow((retrySpec, signal) -> signal.failure());
		}

		RetrySpec spec = Retry.max(retryConfig.getRetries());
		return spec.filter(predicate)
			.doBeforeRetry(signal -> reset(exchange))
			.onRetryExhaustedThrow((retrySpec, signal) -> signal.failure());
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

	public boolean exceedsMaxIterations(ServerWebExchange exchange, RetryConfig retryConfig) {
		Integer iteration = exchange.getAttribute(RETRY_ITERATION_KEY);

		// TODO: deal with null iteration
		boolean exceeds = iteration != null && iteration >= retryConfig.getRetries();
		trace("exceedsMaxIterations %b, iteration %d, configured retries %d", () -> exceeds, () -> iteration,
				retryConfig::getRetries);
		return exceeds;
	}

	@Deprecated
	/**
	 * Use {@link ServerWebExchangeUtils#reset(ServerWebExchange)}
	 */
	public void reset(ServerWebExchange exchange) {
		Connection conn = exchange.getAttribute(ServerWebExchangeUtils.CLIENT_RESPONSE_CONN_ATTR);
		if (conn != null) {
			trace("disposing response connection before next iteration");
		}
		ServerWebExchangeUtils.reset(exchange);
	}

	public GatewayFilter apply(@Nullable String routeId, @Nullable Function<Flux<Long>, ? extends Publisher<?>> repeat,
			@Nullable Retry retry) {
		enableBodyCaching(routeId);
		return (exchange, chain) -> {
			trace("Entering retry-filter");

			// chain.filter returns a Mono<Void>
			Publisher<Void> publisher = chain.filter(exchange)
				// .log("retry-filter", Level.INFO)
				.doOnSuccess(aVoid -> updateIteration(exchange))
				.doOnError(throwable -> updateIteration(exchange));

			if (retry != null) {
				// retryWhen returns a Mono<Void>
				// retry needs to go before repeat
				publisher = ((Mono<Void>) publisher).retryWhen(retry);
			}
			if (repeat != null) {
				// repeatWhen returns a Flux<Void>
				// so this needs to be last and the variable a Publisher<Void>
				publisher = ((Mono<Void>) publisher).repeatWhen(repeat);
			}

			return Mono.fromDirect(publisher);
		};
	}

	private void updateIteration(ServerWebExchange exchange) {
		int iteration = exchange.getAttributeOrDefault(RETRY_ITERATION_KEY, -1);
		int newIteration = iteration + 1;
		trace("setting new iteration in attr %d", () -> newIteration);
		exchange.getAttributes().put(RETRY_ITERATION_KEY, newIteration);
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

		private @Nullable String routeId;

		private int retries = 3;

		private List<Series> series = toList(Series.SERVER_ERROR);

		private List<HttpStatus> statuses = new ArrayList<>();

		private List<HttpMethod> methods = toList(HttpMethod.GET);

		private List<Class<? extends Throwable>> exceptions = toList(IOException.class, TimeoutException.class);

		private @Nullable BackoffConfig backoff;

		private @Nullable JitterConfig jitter;

		private @Nullable Duration timeout;

		public RetryConfig allMethods() {
			return setMethods(HttpMethod.values());
		}

		public void validate() {
			Assert.isTrue(this.retries > 0, "retries must be greater than 0");
			Assert.isTrue(!this.series.isEmpty() || !this.statuses.isEmpty() || !this.exceptions.isEmpty(),
					"series, status and exceptions may not all be empty");
			Assert.notEmpty(this.methods, "methods may not be empty");
			if (this.backoff != null) {
				this.backoff.validate();
			}
			if (this.jitter != null) {
				this.jitter.validate();
			}
			if (this.timeout != null) {
				Assert.isTrue(!timeout.isNegative(), "timeout should be >= 0");
			}
		}

		public @Nullable Duration getTimeout() {
			return timeout;
		}

		public RetryConfig setTimeout(Duration timeout) {
			this.timeout = timeout;
			return this;
		}

		public @Nullable JitterConfig getJitter() {
			return jitter;
		}

		public RetryConfig setJitter(JitterConfig jitter) {
			this.jitter = jitter;
			return this;
		}

		public RetryConfig setJitter(double randomFactor) {
			this.jitter = new JitterConfig(randomFactor);
			return this;
		}

		public @Nullable BackoffConfig getBackoff() {
			return backoff;
		}

		public RetryConfig setBackoff(BackoffConfig backoff) {
			this.backoff = backoff;
			return this;
		}

		public RetryConfig setBackoff(Duration firstBackoff, Duration maxBackoff, int factor,
				boolean basedOnPreviousValue) {
			this.backoff = new BackoffConfig(firstBackoff, maxBackoff, factor, basedOnPreviousValue);
			return this;
		}

		@Override
		public void setRouteId(String routeId) {
			this.routeId = routeId;
		}

		@Override
		public @Nullable String getRouteId() {
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

		private @Nullable Duration maxBackoff;

		private int factor = 2;

		private boolean basedOnPreviousValue = true;

		public BackoffConfig() {
		}

		public BackoffConfig(Duration firstBackoff, Duration maxBackoff, int factor, boolean basedOnPreviousValue) {
			this.firstBackoff = firstBackoff;
			this.maxBackoff = maxBackoff;
			this.factor = factor;
			this.basedOnPreviousValue = basedOnPreviousValue;
		}

		public void validate() {
			Objects.requireNonNull(this.firstBackoff, "firstBackoff must be present");
		}

		public Duration getFirstBackoff() {
			return firstBackoff;
		}

		public void setFirstBackoff(Duration firstBackoff) {
			this.firstBackoff = firstBackoff;
		}

		public @Nullable Duration getMaxBackoff() {
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

		@Override
		public String toString() {
			return new ToStringCreator(this).append("firstBackoff", firstBackoff)
				.append("maxBackoff", maxBackoff)
				.append("factor", factor)
				.append("basedOnPreviousValue", basedOnPreviousValue)
				.toString();
		}

	}

	public static class JitterConfig {

		private double randomFactor = 0.5;

		public void validate() {
			Assert.isTrue(randomFactor >= 0 && randomFactor <= 1,
					"random factor must be between 0 and 1 (default 0.5)");
		}

		public JitterConfig() {
		}

		public JitterConfig(double randomFactor) {
			this.randomFactor = randomFactor;
		}

		public double getRandomFactor() {
			return randomFactor;
		}

		public void setRandomFactor(double randomFactor) {
			this.randomFactor = randomFactor;
		}

		@Override
		public String toString() {
			return new ToStringCreator(this).append("randomFactor", randomFactor).toString();

		}

	}

}
