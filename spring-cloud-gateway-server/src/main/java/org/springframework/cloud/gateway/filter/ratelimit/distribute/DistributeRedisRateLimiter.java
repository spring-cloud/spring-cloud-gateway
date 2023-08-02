/*
 * Copyright 2013-2017 the original author or authors.
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



package org.springframework.cloud.gateway.filter.ratelimit.distribute;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import jakarta.validation.constraints.Min;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.gateway.filter.ratelimit.AbstractRateLimiter;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import org.springframework.cloud.gateway.route.RouteDefinitionRouteLocator;
import org.springframework.cloud.gateway.support.ConfigurationService;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.core.style.ToStringCreator;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.util.Assert;
import org.springframework.validation.annotation.Validated;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@ConfigurationProperties("spring.cloud.gateway.consume-first-redis-rate-limiter")
public class DistributeRedisRateLimiter extends AbstractRateLimiter<DistributeRedisRateLimiter.Config> implements
		ApplicationContextAware {
	public static final String CONFIGURATION_PROPERTY_NAME = "consume-first-redis-rate-limiter";

	public static final String REDIS_SCRIPT_NAME = "consumeFirstRedisRequestRateLimiterScript";

//	private final Logger log = LoggerFactory.getLogger(getClass());

	private Cache<String, RedisReporter> limiterCache;

	private ReactiveStringRedisTemplate redisTemplate;

	private RedisScript<List<Long>> script;

	private final AtomicBoolean initialized = new AtomicBoolean(false);

	private DistributeRedisRateLimiter.Config defaultConfig;

	// configuration properties
	/**
	 * Whether or not to include headers containing rate limiter information, defaults to
	 * true.
	 */
	private boolean includeHeaders = true;

	/**
	 * The name of the header that returns number of remaining requests during the current
	 * second.
	 */
	private String remainingHeader = RedisRateLimiter.REMAINING_HEADER;

	/**
	 * The name of the header that returns the replenish rate configuration.
	 */
	private String replenishRateHeader = RedisRateLimiter.REPLENISH_RATE_HEADER;

	/**
	 * The name of the header that returns the burst capacity configuration.
	 */
	private String burstCapacityHeader = RedisRateLimiter.BURST_CAPACITY_HEADER;

	private int caffeineCapacity = 100;
	private int caffeineMaximumSize = 10000;
	private int caffeineExpireSecs = 10;

	public DistributeRedisRateLimiter(
			ReactiveStringRedisTemplate redisTemplate,
			RedisScript<List<Long>> script,
			ConfigurationService configurationService) {
		super(DistributeRedisRateLimiter.Config.class, CONFIGURATION_PROPERTY_NAME, configurationService);
		this.redisTemplate = redisTemplate;
		this.script = script;
		this.limiterCache = newCache();
		this.initialized.compareAndSet(false, true);
	}

	/**
	 * This creates an instance with default static configuration, useful in Java DSL.
	 *
	 * @param defaultReplenishRate   how many tokens per second in token-bucket algorithm.
	 * @param defaultBurstCapacity   how many tokens the bucket can hold in token-bucket
	 *                               algorithm.
	 * @param defaultRequestedTokens how many tokens are requested per request.
	 */
	public DistributeRedisRateLimiter(int defaultReplenishRate, int defaultBurstCapacity, int defaultRequestedTokens) {
		super(DistributeRedisRateLimiter.Config.class, CONFIGURATION_PROPERTY_NAME, (ConfigurationService) null);
		this.defaultConfig = new Config()
				.setReplenishRate(defaultReplenishRate)
				.setBurstCapacity(defaultBurstCapacity)
				.setReportPeriod(100)
				.setReportMax(defaultReplenishRate / 10)
		;
	}

	@Override
	public void setApplicationContext(ApplicationContext context) throws BeansException {
		if (initialized.compareAndSet(false, true)) {
			if (this.redisTemplate == null) {
				this.redisTemplate = context.getBean(ReactiveStringRedisTemplate.class);
			}
			this.script = context.getBean(REDIS_SCRIPT_NAME, RedisScript.class);
			if (context.getBeanNamesForType(ConfigurationService.class).length > 0) {
				setConfigurationService(context.getBean(ConfigurationService.class));
			}
			if (limiterCache == null) {
				this.limiterCache = newCache();
			}
		}
	}

	@Override
	public Mono<Response> isAllowed(String routeId, String id) {
		if (!this.initialized.get()) {
			throw new IllegalStateException("ConsumeFirstRedisRateLimiter is not initialized");
		}

		if (limiterCache == null) {
			throw new IllegalStateException("ConsumeFirstRedisRateLimiter cache is not initialized");
		}

		Config routeConfig = loadConfiguration(routeId);

		String resource = "consume_first_limiter_" + id;

		return Objects.requireNonNull(limiterCache.get(
						resource,
						key -> new RedisReporter(this, resource, routeConfig))
				)
				.acquire(1)
				.map(allowed -> new Response(allowed, getHeaders(routeConfig, -1L)))
				;
	}


	public boolean isIncludeHeaders() {
		return includeHeaders;
	}

	public void setIncludeHeaders(boolean includeHeaders) {
		this.includeHeaders = includeHeaders;
	}

	public String getRemainingHeader() {
		return remainingHeader;
	}

	public void setRemainingHeader(String remainingHeader) {
		this.remainingHeader = remainingHeader;
	}

	public String getReplenishRateHeader() {
		return replenishRateHeader;
	}

	public void setReplenishRateHeader(String replenishRateHeader) {
		this.replenishRateHeader = replenishRateHeader;
	}

	public String getBurstCapacityHeader() {
		return burstCapacityHeader;
	}

	public void setBurstCapacityHeader(String burstCapacityHeader) {
		this.burstCapacityHeader = burstCapacityHeader;
	}

	public int getCaffeineCapacity() {
		return caffeineCapacity;
	}

	public void setCaffeineCapacity(int caffeineCapacity) {
		this.caffeineCapacity = caffeineCapacity;
	}

	public int getCaffeineMaximumSize() {
		return caffeineMaximumSize;
	}

	public void setCaffeineMaximumSize(int caffeineMaximumSize) {
		this.caffeineMaximumSize = caffeineMaximumSize;
	}

	public int getCaffeineExpireSecs() {
		return caffeineExpireSecs;
	}

	public void setCaffeineExpireSecs(int caffeineExpireSecs) {
		this.caffeineExpireSecs = caffeineExpireSecs;
	}

	DistributeRedisRateLimiter.Config loadConfiguration(String routeId) {
		DistributeRedisRateLimiter.Config routeConfig = getConfig().getOrDefault(routeId, defaultConfig);

		if (routeConfig == null) {
			routeConfig = getConfig().get(RouteDefinitionRouteLocator.DEFAULT_FILTERS);
		}

		if (routeConfig == null) {
			throw new IllegalArgumentException("No Configuration found for route " + routeId + " or defaultFilters");
		}
		return routeConfig;
	}

	public Map<String, String> getHeaders(DistributeRedisRateLimiter.Config config, Long tokensLeft) {
		Map<String, String> headers = new HashMap<>();
		if (isIncludeHeaders()) {
			headers.put(this.remainingHeader, tokensLeft.toString());
			headers.put(this.replenishRateHeader, String.valueOf(config.getReplenishRate()));
			headers.put(this.burstCapacityHeader, String.valueOf(config.getBurstCapacity()));
		}
		return headers;
	}

	private Cache<String, RedisReporter> newCache() {
		return Caffeine.newBuilder()
				.initialCapacity(caffeineCapacity)
				.maximumSize(caffeineMaximumSize)
				.expireAfterAccess(Duration.ofSeconds(caffeineExpireSecs))
				.build();
	}

	public static class RedisReporter {

		private final Logger log = LoggerFactory.getLogger(getClass());
		private final DistributeRedisRateLimiter rateLimiter;
		private final String resource;
		private final DistributeRedisRateLimiter.Config config;
		private final Semaphore semaphore;
		private final AtomicBoolean reportingLockBool = new AtomicBoolean(false);
		private volatile boolean blocked = false;
		private volatile long changedTimestamp = System.currentTimeMillis();

		public RedisReporter(DistributeRedisRateLimiter rateLimiter, String resource,
				DistributeRedisRateLimiter.Config config) {
			this.rateLimiter = rateLimiter;
			this.resource = resource;
			this.config = config;
			semaphore = new Semaphore(config.getReportMax());
		}

		public void setChangedTimestamp(long changedTimestamp) {
			this.changedTimestamp = changedTimestamp;
		}

		public Mono<Boolean> acquire(int count) {
			try {
				return Mono.just(!blocked);
			} finally {
				if (blocked) {
					if (System.currentTimeMillis() - changedTimestamp >= config.getReportPeriod()) {
						tryReport(false).toFuture();
					}
				} else {
					tryReport(!semaphore.tryAcquire(count)).toFuture();
				}
			}
		}

		private Mono<Boolean> tryReport(boolean noToken) {

			if (!addReportingLock()) {
				return Mono.just(false);
			}
			long now = System.currentTimeMillis();
			long timeDiff = now - changedTimestamp;

			if (blocked) {
				setChangedTimestamp(now);
				Mono<ReportResponse> response = report(0);
				return response.map(reportResponse -> {
					setRateLimitStatus(reportResponse);
					if (!blocked) {
						semaphore.release(config.getReportMax() - semaphore.availablePermits());
					}
					return true;
				}).doOnNext(o -> clearReportingLock());
			} else if (noToken || timeDiff >= config.getReportPeriod()) {
				setChangedTimestamp(now);
				int consume = config.getReportMax() - semaphore.availablePermits();
				semaphore.release(consume);
				Mono<ReportResponse> response = report(consume);
				return response.map(reportResponse -> {
					setRateLimitStatus(reportResponse);
					return true;
				}).doOnNext(o -> clearReportingLock());
			} else {
				clearReportingLock();
				return Mono.just(false);
			}
		}

		private boolean addReportingLock() {
			return reportingLockBool.compareAndSet(false, true);
		}

		private void clearReportingLock() {
			reportingLockBool.set(false);
		}

		private void setRateLimitStatus(ReportResponse response) {
			if (response != null && response.isSuccess()) {
				int consume = config.getReportMax() - semaphore.availablePermits();
				blocked = (response.getTokensLeft() <= consume);
			}
		}

		private Mono<ReportResponse> report(int requestedTokens) {
			try {
				List<String> keys = Collections.singletonList(resource);

				// The arguments to the LUA script. time() returns unixtime in seconds.
				List<String> scriptArgs = Arrays.asList(String.valueOf(config.getBurstCapacity()), String.valueOf(config.getReplenishRate()), String.valueOf(requestedTokens));
				// tokens_left = redis.eval(SCRIPT, keys, args)
				Flux<List<Long>> flux = rateLimiter.redisTemplate.execute(rateLimiter.script, keys, scriptArgs);
				// .log("redisratelimiter", Level.FINER);
				return flux.onErrorResume(throwable -> {
					log.error("ConsumeFirstRedisRateLimiter Error calling rate limiter lua", throwable);
					return Flux.just(Collections.singletonList(-1L));
				}).reduce(new ArrayList<Long>(), (longs, l) -> {
					longs.addAll(l);
					return longs;
				}).map(results -> {
					Long tokensLeft = results.get(0);
					return new ReportResponse(tokensLeft >= 0, tokensLeft);
				});
			} catch (Exception e) {
				log.error("ConsumeFirstRedisRateLimiter Error determining if user allowed from redis", e);
				return Mono.just(new ReportResponse(false, -1L));
			}
		}
	}

	public static class ReportResponse {
		private final boolean success;
		private final long tokensLeft;

		public ReportResponse(boolean success, long tokensLeft) {
			this.success = success;
			this.tokensLeft = tokensLeft;
		}

		public boolean isSuccess() {
			return success;
		}

		public long getTokensLeft() {
			return tokensLeft;
		}
	}

	@Validated
	public static class Config {

		@Min(1)
		private int replenishRate;

		@Min(0)
		private int burstCapacity = 1;

		/**
		 * max consumed counts to fire a report
		 */
		@Min(1)
		private int reportMax = 1;

		/**
		 * max report duration (ms)
		 */
		@Min(1)
		private int reportPeriod = 10;

		public int getReplenishRate() {
			return replenishRate;
		}

		public DistributeRedisRateLimiter.Config setReplenishRate(int replenishRate) {
			this.replenishRate = replenishRate;
			return this;
		}

		public int getBurstCapacity() {
			return burstCapacity;
		}

		public DistributeRedisRateLimiter.Config setBurstCapacity(int burstCapacity) {
			Assert.isTrue(burstCapacity >= this.replenishRate,
					"BurstCapacity(" + burstCapacity + ") must be greater than or equal than replenishRate("
							+ this.replenishRate + ")");
			this.burstCapacity = burstCapacity;
			return this;
		}

		public int getReportMax() {
			return reportMax;
		}

		public DistributeRedisRateLimiter.Config setReportMax(int reportMax) {
			this.reportMax = reportMax;
			return this;
		}

		public int getReportPeriod() {
			return reportPeriod;
		}

		public DistributeRedisRateLimiter.Config setReportPeriod(int reportPeriod) {
			this.reportPeriod = reportPeriod;
			return this;
		}

		@Override
		public String toString() {
			return new ToStringCreator(this).append("replenishRate", replenishRate)
					.append("burstCapacity", burstCapacity).append("reportMax", reportMax)
					.append("reportPeriod", reportPeriod).toString();
		}

	}
}
