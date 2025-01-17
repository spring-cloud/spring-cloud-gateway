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
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.function.Supplier;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.BandwidthBuilder.BandwidthBuilderBuildStage;
import io.github.bucket4j.BandwidthBuilder.BandwidthBuilderRefillStage;
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
import org.springframework.util.Assert;

public class Bucket4jRateLimiter extends AbstractRateLimiter<Bucket4jRateLimiter.Config> {

	/**
	 * Default Header Name.
	 */
	public static final String DEFAULT_HEADER_NAME = "X-RateLimit-Remaining";

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

		AsyncBucketProxy bucket = proxyManager.builder().build(id, routeConfig.getConfigurationSupplier());
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
		// TODO: configurable isIncludeHeaders?
		// if (isIncludeHeaders()) {
		headers.put(config.getHeaderName(), tokensLeft.toString());
		// }
		return headers;
	}

	public static class Config {

		private static final Function<Config, BucketConfiguration> DEFAULT_CONFIGURATION_BUILDER = config -> {
			BandwidthBuilderRefillStage bandwidth = Bandwidth.builder().capacity(config.getCapacity());

			long refillTokens = config.getRefillTokens() == null ? config.getCapacity() : config.getRefillTokens();

			BandwidthBuilderBuildStage refill = switch (config.getRefillStyle()) {
				case GREEDY -> bandwidth.refillGreedy(refillTokens, config.getRefillPeriod());
				case INTERVALLY -> bandwidth.refillIntervally(refillTokens, config.getRefillPeriod());
				case INTERVALLY_ALIGNED -> bandwidth.refillIntervallyAligned(refillTokens, config.getRefillPeriod(),
						config.getTimeOfFirstRefill());
			};

			return BucketConfiguration.builder().addLimit(refill.build()).build();
		};

		long capacity;

		Function<Config, BucketConfiguration> configurationBuilder = DEFAULT_CONFIGURATION_BUILDER;

		Supplier<CompletableFuture<BucketConfiguration>> configurationSupplier;

		String headerName = DEFAULT_HEADER_NAME;

		Duration refillPeriod;

		RefillStyle refillStyle = RefillStyle.GREEDY;

		Long refillTokens;

		long requestedTokens = 1;

		// for RefillStyle.INTERVALLY_ALIGNED
		Instant timeOfFirstRefill;

		public long getCapacity() {
			return capacity;
		}

		public Config setCapacity(long capacity) {
			this.capacity = capacity;
			return this;
		}

		public Function<Config, BucketConfiguration> getConfigurationBuilder() {
			return configurationBuilder;
		}

		public void setConfigurationBuilder(Function<Config, BucketConfiguration> configurationBuilder) {
			Assert.notNull(configurationBuilder, "configurationBuilder may not be null");
			this.configurationBuilder = configurationBuilder;
		}

		public Supplier<CompletableFuture<BucketConfiguration>> getConfigurationSupplier() {
			if (configurationSupplier != null) {
				return configurationSupplier;
			}
			return () -> CompletableFuture.completedFuture(getConfigurationBuilder().apply(this));
		}

		public void setConfigurationSupplier(Function<Config, BucketConfiguration> configurationBuilder) {
			Assert.notNull(configurationBuilder, "configurationBuilder may not be null");
			this.configurationBuilder = configurationBuilder;
		}

		public String getHeaderName() {
			return headerName;
		}

		public Config setHeaderName(String headerName) {
			Assert.notNull(headerName, "headerName may not be null");
			this.headerName = headerName;
			return this;
		}

		public Duration getRefillPeriod() {
			return refillPeriod;
		}

		public Config setRefillPeriod(Duration refillPeriod) {
			this.refillPeriod = refillPeriod;
			return this;
		}

		public RefillStyle getRefillStyle() {
			return refillStyle;
		}

		public Config setRefillStyle(RefillStyle refillStyle) {
			this.refillStyle = refillStyle;
			return this;
		}

		public Long getRefillTokens() {
			return refillTokens;
		}

		public Config setRefillTokens(Long refillTokens) {
			this.refillTokens = refillTokens;
			return this;
		}

		public long getRequestedTokens() {
			return requestedTokens;
		}

		public Config setRequestedTokens(long requestedTokens) {
			this.requestedTokens = requestedTokens;
			return this;
		}

		public Instant getTimeOfFirstRefill() {
			return timeOfFirstRefill;
		}

		public Config setTimeOfFirstRefill(Instant timeOfFirstRefill) {
			this.timeOfFirstRefill = timeOfFirstRefill;
			return this;
		}

		public String toString() {
			return new ToStringCreator(this).append("capacity", capacity)
				.append("headerName", headerName)
				.append("refillPeriod", refillPeriod)
				.append("refillStyle", refillStyle)
				.append("refillTokens", refillTokens)
				.append("requestedTokens", requestedTokens)
				.append("timeOfFirstRefill", timeOfFirstRefill)
				.toString();
		}

	}

	public enum RefillStyle {

		/**
		 * Greedy tries to add the tokens to the bucket as soon as possible.
		 */
		GREEDY,

		/**
		 * Intervally, in opposite to greedy, waits until the whole refillPeriod has
		 * elapsed before refilling tokens.
		 */
		INTERVALLY,

		/**
		 * IntervallyAligned, like Intervally, but with a specified first refill time.
		 */
		INTERVALLY_ALIGNED;

	}

}
