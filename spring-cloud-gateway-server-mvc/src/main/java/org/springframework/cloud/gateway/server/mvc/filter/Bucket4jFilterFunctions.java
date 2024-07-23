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

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
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
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.function.HandlerFilterFunction;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;

public abstract class Bucket4jFilterFunctions {

	/**
	 * Default Header Name.
	 */
	public static final String DEFAULT_HEADER_NAME = "X-RateLimit-Remaining";

	private static final Function<RateLimitConfig, BucketConfiguration> DEFAULT_CONFIGURATION_BUILDER = config -> BucketConfiguration
		.builder()
		.addLimit(Bandwidth.builder()
			.capacity(config.getCapacity())
			.refillGreedy(config.getCapacity(), config.getPeriod())
			.build())
		.build();

	private Bucket4jFilterFunctions() {
	}

	public static HandlerFilterFunction<ServerResponse, ServerResponse> rateLimit(long capacity, Duration period,
			Function<ServerRequest, String> keyResolver) {
		return rateLimit(c -> c.setCapacity(capacity).setPeriod(period).setKeyResolver(keyResolver));
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	public static HandlerFilterFunction<ServerResponse, ServerResponse> rateLimit(
			Consumer<RateLimitConfig> configConsumer) {
		RateLimitConfig config = new RateLimitConfig();
		configConsumer.accept(config);
		BucketConfiguration bucketConfiguration = config.getConfigurationBuilder().apply(config);
		return (request, next) -> {
			AsyncProxyManager proxyManager = MvcUtils.getApplicationContext(request).getBean(AsyncProxyManager.class);
			String key = config.getKeyResolver().apply(request);
			if (!StringUtils.hasText(key)) {
				// TODO: configurable empty key status code
				return ServerResponse.status(HttpStatus.FORBIDDEN).build();
			}
			AsyncBucketProxy bucket = proxyManager.builder().build(key, bucketConfiguration);
			CompletableFuture<ConsumptionProbe> bucketFuture = bucket.tryConsumeAndReturnRemaining(config.getTokens());
			ConsumptionProbe consumptionProbe;
			if (config.getTimeout() != null) {
				consumptionProbe = bucketFuture.get(config.getTimeout().toMillis(), TimeUnit.MILLISECONDS);
			}
			else {
				consumptionProbe = bucketFuture.get();
			}
			boolean allowed = consumptionProbe.isConsumed();
			long remainingTokens = consumptionProbe.getRemainingTokens();
			if (allowed) {
				ServerResponse serverResponse = next.handle(request);
				serverResponse.headers().add(config.getHeaderName(), String.valueOf(remainingTokens));
				return serverResponse;
			}
			return ServerResponse.status(config.getStatusCode())
				.header(config.getHeaderName(), String.valueOf(remainingTokens))
				.build();
		};
	}

	public static class RateLimitConfig {

		Function<RateLimitConfig, BucketConfiguration> configurationBuilder = DEFAULT_CONFIGURATION_BUILDER;

		long capacity;

		Duration period;

		Function<ServerRequest, String> keyResolver;

		HttpStatusCode statusCode = HttpStatus.TOO_MANY_REQUESTS;

		Duration timeout;

		int tokens = 1;

		String headerName = DEFAULT_HEADER_NAME;

		public Function<RateLimitConfig, BucketConfiguration> getConfigurationBuilder() {
			return configurationBuilder;
		}

		public void setConfigurationBuilder(Function<RateLimitConfig, BucketConfiguration> configurationBuilder) {
			Assert.notNull(configurationBuilder, "configurationBuilder may not be null");
			this.configurationBuilder = configurationBuilder;
		}

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
			Assert.notNull(keyResolver, "keyResolver may not be null");
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

		public Duration getTimeout() {
			return timeout;
		}

		public RateLimitConfig setTimeout(Duration timeout) {
			this.timeout = timeout;
			return this;
		}

		public int getTokens() {
			return tokens;
		}

		public RateLimitConfig setTokens(int tokens) {
			Assert.isTrue(tokens > 0, "tokens must be greater than zero");
			this.tokens = tokens;
			return this;
		}

		public String getHeaderName() {
			return headerName;
		}

		public RateLimitConfig setHeaderName(String headerName) {
			Assert.notNull(headerName, "headerName may not be null");
			this.headerName = headerName;
			return this;
		}

	}

	public static class FilterSupplier extends SimpleFilterSupplier {

		public FilterSupplier() {
			super(Bucket4jFilterFunctions.class);
		}

	}

}
