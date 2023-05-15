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

package org.springframework.cloud.gateway.filter.ratelimit;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.distributed.AsyncBucketProxy;
import io.github.bucket4j.distributed.proxy.AsyncProxyManager;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import reactor.core.publisher.Mono;

import org.springframework.cloud.gateway.route.RouteDefinitionRouteLocator;
import org.springframework.cloud.gateway.support.ConfigurationService;
import org.springframework.core.style.ToStringCreator;

public class Bucket4jRateLimiter extends AbstractRateLimiter<Bucket4jRateLimiter.Config> {

	/**
	 * Redis Rate Limiter property name.
	 */
	public static final String CONFIGURATION_PROPERTY_NAME = "bucket4j-rate-limiter";

	private final Log log = LogFactory.getLog(getClass());

	private final AsyncProxyManager<String> proxyManager;

	private Config defaultConfig = new Config();

	public Bucket4jRateLimiter(AsyncProxyManager<String> proxyManager, ConfigurationService configurationService) {
		super(Config.class, CONFIGURATION_PROPERTY_NAME, configurationService);
		this.proxyManager = proxyManager;
	}

	@Override
	public Mono<Response> isAllowed(String routeId, String id) {
		Config routeConfig = loadRouteConfiguration(routeId);

		BucketConfiguration bucketConfiguration = getBucketConfiguration(routeConfig);

		AsyncBucketProxy bucket = proxyManager.builder().build(id, bucketConfiguration);
		CompletableFuture<ConsumptionProbe> bucketFuture = bucket
				.tryConsumeAndReturnRemaining(routeConfig.getRequestedTokens());
		return Mono.fromFuture(bucketFuture).onErrorResume(throwable -> {
			if (log.isDebugEnabled()) {
				log.debug("Error calling Bucket4J rate limiter", throwable);
			}
			return Mono.just(ConsumptionProbe.rejected(-1, -1, -1));
		}).map(consumptionProbe -> {
			boolean allowed = consumptionProbe.isConsumed();
			long remainingTokens = consumptionProbe.getRemainingTokens();
			Response response = new Response(allowed, getHeaders(routeConfig, remainingTokens));

			if (log.isDebugEnabled()) {
				log.debug("response: " + response);
			}
			return response;
		});
	}

	protected static BucketConfiguration getBucketConfiguration(Config routeConfig) {
		return BucketConfiguration.builder()
				.addLimit(Bandwidth.simple(routeConfig.getCapacity(), routeConfig.getPeriod())).build();
	}

	protected Config loadRouteConfiguration(String routeId) {
		Config routeConfig = getConfig().getOrDefault(routeId, defaultConfig);

		if (routeConfig == null) {
			routeConfig = getConfig().get(RouteDefinitionRouteLocator.DEFAULT_FILTERS);
		}

		if (routeConfig == null) {
			throw new IllegalArgumentException("No Configuration found for route " + routeId + " or defaultFilters");
		}
		return routeConfig;
	}

	public Map<String, String> getHeaders(Config config, Long tokensLeft) {
		Map<String, String> headers = new HashMap<>();
		// if (isIncludeHeaders()) {
		// TODO: configurable headers ala RedisRateLimiter
		headers.put("X-RateLimit-Remaining", tokensLeft.toString());
		// }
		return headers;
	}

	public static class Config {

		//TODO: create simple and classic w/Refill

		long capacity;

		Duration period;

		private long requestedTokens = 1;

		public long getCapacity() {
			return capacity;
		}

		public Config setCapacity(long capacity) {
			this.capacity = capacity;
			return this;
		}

		public Duration getPeriod() {
			return period;
		}

		public Config setPeriod(Duration period) {
			this.period = period;
			return this;
		}

		public long getRequestedTokens() {
			return requestedTokens;
		}

		public Config setRequestedTokens(long requestedTokens) {
			this.requestedTokens = requestedTokens;
			return this;
		}

		public String toString() {
			return new ToStringCreator(this).append("capacity", capacity).append("requestedTokens", requestedTokens)
					.append("period", period).toString();
		}

	}

}
