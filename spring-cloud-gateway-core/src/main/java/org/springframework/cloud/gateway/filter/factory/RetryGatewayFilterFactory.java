/*
 * Copyright 2013-2018 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package org.springframework.cloud.gateway.filter.factory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;
import java.util.logging.Level;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import reactor.core.publisher.Mono;
import reactor.retry.Repeat;
import reactor.retry.RepeatContext;
import reactor.retry.Retry;
import reactor.retry.RetryContext;

import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatus.Series;
import org.springframework.util.Assert;
import org.springframework.web.server.ServerWebExchange;

public class RetryGatewayFilterFactory extends AbstractGatewayFilterFactory<RetryGatewayFilterFactory.RetryConfig> {
	private static final Log log = LogFactory.getLog(RetryGatewayFilterFactory.class);

	public RetryGatewayFilterFactory() {
		super(RetryConfig.class);
	}

	@Override
	public GatewayFilter apply(RetryConfig retryConfig) {
		retryConfig.validate();

		Predicate<? super RepeatContext<ServerWebExchange>> predicate = context -> {
			ServerWebExchange exchange = context.applicationContext();
			if (exceedsMaxIterations(exchange, retryConfig)) {
				return false;
			}

			HttpStatus statusCode = exchange.getResponse().getStatusCode();
			HttpMethod httpMethod = exchange.getRequest().getMethod();

			boolean retryableStatusCode = retryConfig.getStatuses().contains(statusCode);

			if (!retryableStatusCode && statusCode != null) { // null status code might mean a network exception?
				// try the series
				retryableStatusCode = retryConfig.getSeries().stream()
						.anyMatch(series -> statusCode.series().equals(series));
			}

			boolean retryableMethod = retryConfig.getMethods().contains(httpMethod);
			return retryableMethod && retryableStatusCode;
		};

		Repeat<ServerWebExchange> repeat = Repeat.onlyIf(predicate)
				.doOnRepeat(context -> reset(context.applicationContext()));

		//TODO: support timeout, backoff, jitter, etc... in Builder

		Predicate<RetryContext<ServerWebExchange>> retryContextPredicate = context -> {
			if (exceedsMaxIterations(context.applicationContext(), retryConfig)) {
				return false;
			}

			for (Class<? extends Throwable> clazz : retryConfig.getExceptions()) {
				if (clazz.isInstance(context.exception())) {
					return true;
				}
			}
            return false;
		};

		Retry<ServerWebExchange> reactorRetry = Retry.onlyIf(retryContextPredicate)
				.doOnRetry(context -> reset(context.applicationContext()))
				.retryMax(retryConfig.getRetries());
		return apply(repeat, reactorRetry);
	}

	public boolean exceedsMaxIterations(ServerWebExchange exchange, RetryConfig retryConfig) {
		Integer iteration = exchange.getAttribute("retry_iteration");

		//TODO: deal with null iteration
		return iteration != null && iteration >= retryConfig.getRetries();
	}

	public void reset(ServerWebExchange exchange) {
		//TODO: what else to do to reset SWE?
		exchange.getAttributes().remove(ServerWebExchangeUtils.GATEWAY_ALREADY_ROUTED_ATTR);
	}

	@Deprecated
	public GatewayFilter apply(Repeat<ServerWebExchange> repeat) {
		return apply(repeat, Retry.onlyIf(ctxt -> false));
	}

	public GatewayFilter apply(Repeat<ServerWebExchange> repeat, Retry<ServerWebExchange> retry) {
		return (exchange, chain) -> {
			log.trace("Entering retry-filter");

			int iteration = exchange.getAttributeOrDefault("retry_iteration", -1);
			exchange.getAttributes().put("retry_iteration", iteration + 1);

			return Mono.fromDirect(chain.filter(exchange)
					.log("retry-filter", Level.INFO)
					.retryWhen(retry.withApplicationContext(exchange))
					.repeatWhen(repeat.withApplicationContext(exchange)));
		};
	}

	private static <T> List<T> toList(T item) {
		ArrayList<T> list = new ArrayList<>();
		list.add(item);
		return list;
	}

	public static class RetryConfig {
		private int retries = 3;
		
		private List<Series> series = toList(Series.SERVER_ERROR);
		
		private List<HttpStatus> statuses = new ArrayList<>();
		
		private List<HttpMethod> methods = toList(HttpMethod.GET);

		private List<Class<? extends Throwable>> exceptions = toList(IOException.class);

		public RetryConfig setRetries(int retries) {
			this.retries = retries;
			return this;
		}
		
		public RetryConfig setSeries(Series... series) {
			this.series = Arrays.asList(series);
			return this;
		}
		
		public RetryConfig setStatuses(HttpStatus... statuses) {
			this.statuses = Arrays.asList(statuses);
			return this;
		}
		
		public RetryConfig setMethods(HttpMethod... methods) {
			this.methods = Arrays.asList(methods);
			return this;
		}

		public RetryConfig allMethods() {
			return setMethods(HttpMethod.values());
		}

		public RetryConfig setExceptions(Class<? extends Throwable>... exceptions) {
			this.exceptions = Arrays.asList(exceptions);
			return this;
		}

		public void validate() {
			Assert.isTrue(this.retries > 0, "retries must be greater than 0");
			Assert.isTrue(!this.series.isEmpty() || !this.statuses.isEmpty(),
					"series and status may not both be empty");
			Assert.notEmpty(this.methods, "methods may not be empty");
		}

		public int getRetries() {
			return retries;
		}

		public List<Series> getSeries() {
			return series;
		}

		public List<HttpStatus> getStatuses() {
			return statuses;
		}

		public List<HttpMethod> getMethods() {
			return methods;
		}

		public List<Class<? extends Throwable>> getExceptions() {
			return exceptions;
		}

	}
}
