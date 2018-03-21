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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;

import reactor.retry.Repeat;
import reactor.retry.RepeatContext;

import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatus.Series;
import org.springframework.util.Assert;
import org.springframework.web.server.ServerWebExchange;

public class RetryGatewayFilterFactory extends AbstractGatewayFilterFactory<RetryGatewayFilterFactory.Retry> {

	public RetryGatewayFilterFactory() {
		super(Retry.class);
	}

	@Override
	public GatewayFilter apply(Retry retry) {
		retry.validate();

		Predicate<? super RepeatContext<ServerWebExchange>> predicate = context -> {
			ServerWebExchange exchange = context.applicationContext();
			HttpStatus statusCode = exchange.getResponse().getStatusCode();
			HttpMethod httpMethod = exchange.getRequest().getMethod();

			boolean retryableStatusCode = retry.getStatuses().contains(statusCode);

			if (!retryableStatusCode) {
				// try the series
				retryableStatusCode = retry.getSeries().stream()
						.anyMatch(series -> statusCode.series().equals(series));
			}

			boolean retryableMethod = retry.getMethods().contains(httpMethod);
			return retryableMethod && retryableStatusCode;
		};

		Repeat<ServerWebExchange> repeat = Repeat.create(predicate, retry.getRetries());

		//TODO: support timeout, backoff, jitter, etc... in Builder
		return apply(repeat);
	}

	public GatewayFilter apply(Repeat<ServerWebExchange> repeat) {
		return (exchange, chain) -> chain.filter(exchange).repeatWhen(
                repeat.withApplicationContext(exchange)).next();
	}

	private static <T> List<T> toList(T item) {
		ArrayList<T> list = new ArrayList<>();
		list.add(item);
		return list;
	}

	public static class Retry {
		private int retries = 3;
		
		private List<Series> series = toList(Series.SERVER_ERROR);
		
		private List<HttpStatus> statuses = new ArrayList<>();
		
		private List<HttpMethod> methods = toList(HttpMethod.GET);
		
		public Retry setRetries(int retries) {
			this.retries = retries;
			return this;
		}
		
		public Retry setSeries(Series... series) {
			this.series = Arrays.asList(series);
			return this;
		}
		
		public Retry setStatuses(HttpStatus... statuses) {
			this.statuses = Arrays.asList(statuses);
			return this;
		}
		
		public Retry setMethods(HttpMethod... methods) {
			this.methods = Arrays.asList(methods);
			return this;
		}

		public Retry allMethods() {
			return setMethods(HttpMethod.values());
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
	}
}
