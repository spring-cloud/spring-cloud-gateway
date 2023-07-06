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

import java.lang.reflect.Method;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collection;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Function;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.distributed.AsyncBucketProxy;
import io.github.bucket4j.distributed.proxy.AsyncProxyManager;

import org.springframework.cloud.gateway.server.mvc.common.MvcUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.servlet.function.HandlerFilterFunction;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;

public abstract class Bucket4jFilterFunctions {

	private Bucket4jFilterFunctions() {
	}

	public static HandlerFilterFunction<ServerResponse, ServerResponse> rateLimit(long capacity, Duration period,
			Function<ServerRequest, String> keyResolver) {
		return rateLimit(c -> c.setCapacity(capacity).setPeriod(period).setKeyResolver(keyResolver));
	}

	public static HandlerFilterFunction<ServerResponse, ServerResponse> rateLimit(
			Consumer<RateLimitConfig> configConsumer) {
		RateLimitConfig config = new RateLimitConfig();
		configConsumer.accept(config);
		BucketConfiguration bucketConfiguration = BucketConfiguration.builder()
				.addLimit(Bandwidth.simple(config.getCapacity(), config.getPeriod())).build();
		return (request, next) -> {
			AsyncProxyManager proxyManager = MvcUtils.getApplicationContext(request).getBean(AsyncProxyManager.class);
			AsyncBucketProxy bucket = proxyManager.builder().build(config.getKeyResolver().apply(request),
					bucketConfiguration);
			// TODO: configurable tokens
			CompletableFuture<ConsumptionProbe> bucketFuture = bucket.tryConsumeAndReturnRemaining(1);
			// TODO: configurable timeout
			ConsumptionProbe consumptionProbe = bucketFuture.get();
			boolean allowed = consumptionProbe.isConsumed();
			long remainingTokens = consumptionProbe.getRemainingTokens();
			if (allowed) {
				ServerResponse serverResponse = next.handle(request);
				// TODO: configurable headers
				serverResponse.headers().add("X-RateLimit-Remaining", String.valueOf(remainingTokens));
				return serverResponse;
			}
			return ServerResponse.status(config.getStatusCode())
					.header("X-RateLimit-Remaining", String.valueOf(remainingTokens)).build();
		};
	}

	public static class RateLimitConfig {

		long capacity;

		Duration period;

		Function<ServerRequest, String> keyResolver;

		HttpStatusCode statusCode = HttpStatus.TOO_MANY_REQUESTS;

		public long getCapacity() {
			return capacity;
		}

		public RateLimitConfig setCapacity(long capacity) {
			this.capacity = capacity;
			return this;
		}

		public Duration getPeriod() {
			return period;
		}

		public RateLimitConfig setPeriod(Duration period) {
			this.period = period;
			return this;
		}

		public Function<ServerRequest, String> getKeyResolver() {
			return keyResolver;
		}

		public RateLimitConfig setKeyResolver(Function<ServerRequest, String> keyResolver) {
			this.keyResolver = keyResolver;
			return this;
		}

		public HttpStatusCode getStatusCode() {
			return statusCode;
		}

		public RateLimitConfig setStatusCode(HttpStatusCode statusCode) {
			this.statusCode = statusCode;
			return this;
		}

	}

	static class FilterSupplier implements org.springframework.cloud.gateway.server.mvc.filter.FilterSupplier {

		@Override
		public Collection<Method> get() {
			return Arrays.asList(Bucket4jFilterFunctions.class.getMethods());
		}

	}

}
