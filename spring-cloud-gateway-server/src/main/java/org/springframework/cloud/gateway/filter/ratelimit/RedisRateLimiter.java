/*
 * Copyright 2013-2020 the original author or authors.
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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import jakarta.validation.constraints.Min;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.springframework.beans.BeansException;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.gateway.route.RouteDefinitionRouteLocator;
import org.springframework.cloud.gateway.support.ConfigurationService;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.core.style.ToStringCreator;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.util.Assert;
import org.springframework.validation.annotation.Validated;

/**
 * See https://stripe.com/blog/rate-limiters and
 * https://gist.github.com/ptarjan/e38f45f2dfe601419ca3af937fff574d#file-1-check_request_rate_limiter-rb-L11-L34.
 *
 * @author Spencer Gibb
 * @author Ronny Bräunlich
 * @author Denis Cutic
 */
@ConfigurationProperties("spring.cloud.gateway.redis-rate-limiter")
public class RedisRateLimiter extends AbstractRateLimiter<RedisRateLimiter.Config> implements ApplicationContextAware {

	/**
	 * Redis Rate Limiter property name.
	 */
	public static final String CONFIGURATION_PROPERTY_NAME = "redis-rate-limiter";

	/**
	 * Redis Script name.
	 */
	public static final String REDIS_SCRIPT_NAME = "redisRequestRateLimiterScript";

	/**
	 * Remaining Rate Limit header name.
	 */
	public static final String REMAINING_HEADER = "X-RateLimit-Remaining";

	/**
	 * Replenish Rate Limit header name.
	 */
	public static final String REPLENISH_RATE_HEADER = "X-RateLimit-Replenish-Rate";

	/**
	 * Burst Capacity header name.
	 */
	public static final String BURST_CAPACITY_HEADER = "X-RateLimit-Burst-Capacity";

	/**
	 * Requested Tokens header name.
	 */
	public static final String REQUESTED_TOKENS_HEADER = "X-RateLimit-Requested-Tokens";

	private Log log = LogFactory.getLog(getClass());

	private ReactiveStringRedisTemplate redisTemplate;

	private RedisScript<List<Long>> script;

	private AtomicBoolean initialized = new AtomicBoolean(false);

	private Config defaultConfig;

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
	private String remainingHeader = REMAINING_HEADER;

	/** The name of the header that returns the replenish rate configuration. */
	private String replenishRateHeader = REPLENISH_RATE_HEADER;

	/** The name of the header that returns the burst capacity configuration. */
	private String burstCapacityHeader = BURST_CAPACITY_HEADER;

	/** The name of the header that returns the requested tokens configuration. */
	private String requestedTokensHeader = REQUESTED_TOKENS_HEADER;

	public RedisRateLimiter(ReactiveStringRedisTemplate redisTemplate, RedisScript<List<Long>> script,
			ConfigurationService configurationService) {
		super(Config.class, CONFIGURATION_PROPERTY_NAME, configurationService);
		this.redisTemplate = redisTemplate;
		this.script = script;
		this.initialized.compareAndSet(false, true);
	}

	/**
	 * This creates an instance with default static configuration, useful in Java DSL.
	 * @param defaultReplenishRate how many tokens per second in token-bucket algorithm.
	 * @param defaultBurstCapacity how many tokens the bucket can hold in token-bucket
	 * algorithm.
	 */
	public RedisRateLimiter(int defaultReplenishRate, int defaultBurstCapacity) {
		super(Config.class, CONFIGURATION_PROPERTY_NAME, (ConfigurationService) null);
		this.defaultConfig = new Config().setReplenishRate(defaultReplenishRate).setBurstCapacity(defaultBurstCapacity);
	}

	/**
	 * This creates an instance with default static configuration, useful in Java DSL.
	 * @param defaultReplenishRate how many tokens per second in token-bucket algorithm.
	 * @param defaultBurstCapacity how many tokens the bucket can hold in token-bucket
	 * algorithm.
	 * @param defaultRequestedTokens how many tokens are requested per request.
	 */
	public RedisRateLimiter(int defaultReplenishRate, int defaultBurstCapacity, int defaultRequestedTokens) {
		this(defaultReplenishRate, defaultBurstCapacity);
		this.defaultConfig.setRequestedTokens(defaultRequestedTokens);
	}

	static List<String> getKeys(String id) {
		// use `{}` around keys to use Redis Key hash tags
		// this allows for using redis cluster

		// Make a unique key per user.
		String prefix = "request_rate_limiter.{" + id;

		// You need two Redis keys for Token Bucket.
		String tokenKey = prefix + "}.tokens";
		String timestampKey = prefix + "}.timestamp";
		return Arrays.asList(tokenKey, timestampKey);
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

	public String getRequestedTokensHeader() {
		return requestedTokensHeader;
	}

	public void setRequestedTokensHeader(String requestedTokensHeader) {
		this.requestedTokensHeader = requestedTokensHeader;
	}

	/**
	 * Used when setting default configuration in constructor.
	 * @param context the ApplicationContext object to be used by this object
	 * @throws BeansException if thrown by application context methods
	 */
	@Override
	@SuppressWarnings("unchecked")
	public void setApplicationContext(ApplicationContext context) throws BeansException {
		if (initialized.compareAndSet(false, true)) {
			if (this.redisTemplate == null) {
				this.redisTemplate = context.getBean(ReactiveStringRedisTemplate.class);
			}
			this.script = context.getBean(REDIS_SCRIPT_NAME, RedisScript.class);
			if (context.getBeanNamesForType(ConfigurationService.class).length > 0) {
				setConfigurationService(context.getBean(ConfigurationService.class));
			}
		}
	}

	/* for testing */ Config getDefaultConfig() {
		return defaultConfig;
	}

	/**
	 * This uses a basic token bucket algorithm and relies on the fact that Redis scripts
	 * execute atomically. No other operations can run between fetching the count and
	 * writing the new count.
	 */
	@Override
	@SuppressWarnings("unchecked")
	public Mono<Response> isAllowed(String routeId, String id) {
		if (!this.initialized.get()) {
			throw new IllegalStateException("RedisRateLimiter is not initialized");
		}

		Config routeConfig = loadConfiguration(routeId);

		// How many requests per second do you want a user to be allowed to do?
		int replenishRate = routeConfig.getReplenishRate();

		// How much bursting do you want to allow?
		int burstCapacity = routeConfig.getBurstCapacity();

		// How many tokens are requested per request?
		int requestedTokens = routeConfig.getRequestedTokens();

		try {
			List<String> keys = getKeys(id);

			// The arguments to the LUA script. time() returns unixtime in seconds.
			List<String> scriptArgs = Arrays.asList(replenishRate + "", burstCapacity + "", "", requestedTokens + "");
			// allowed, tokens_left = redis.eval(SCRIPT, keys, args)
			Flux<List<Long>> flux = this.redisTemplate.execute(this.script, keys, scriptArgs);
			// .log("redisratelimiter", Level.FINER);
			return flux.onErrorResume(throwable -> {
				if (log.isDebugEnabled()) {
					log.debug("Error calling rate limiter lua", throwable);
				}
				return Flux.just(Arrays.asList(1L, -1L));
			}).reduce(new ArrayList<Long>(), (longs, l) -> {
				longs.addAll(l);
				return longs;
			}).map(results -> {
				boolean allowed = results.get(0) == 1L;
				Long tokensLeft = results.get(1);

				Response response = new Response(allowed, getHeaders(routeConfig, tokensLeft));

				if (log.isDebugEnabled()) {
					log.debug("response: " + response);
				}
				return response;
			});
		}
		catch (Exception e) {
			/*
			 * We don't want a hard dependency on Redis to allow traffic. Make sure to set
			 * an alert so you know if this is happening too much. Stripe's observed
			 * failure rate is 0.01%.
			 */
			log.error("Error determining if user allowed from redis", e);
		}
		return Mono.just(new Response(true, getHeaders(routeConfig, -1L)));
	}

	/* for testing */ Config loadConfiguration(String routeId) {
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
		if (isIncludeHeaders()) {
			headers.put(this.remainingHeader, tokensLeft.toString());
			headers.put(this.replenishRateHeader, String.valueOf(config.getReplenishRate()));
			headers.put(this.burstCapacityHeader, String.valueOf(config.getBurstCapacity()));
			headers.put(this.requestedTokensHeader, String.valueOf(config.getRequestedTokens()));
		}
		return headers;
	}

	@Validated
	public static class Config {

		@Min(1)
		private int replenishRate;

		@Min(0)
		private int burstCapacity = 1;

		@Min(1)
		private int requestedTokens = 1;

		public int getReplenishRate() {
			return replenishRate;
		}

		public Config setReplenishRate(int replenishRate) {
			this.replenishRate = replenishRate;
			return this;
		}

		public int getBurstCapacity() {
			return burstCapacity;
		}

		public Config setBurstCapacity(int burstCapacity) {
			Assert.isTrue(burstCapacity >= this.replenishRate, "BurstCapacity(" + burstCapacity
					+ ") must be greater than or equal than replenishRate(" + this.replenishRate + ")");
			this.burstCapacity = burstCapacity;
			return this;
		}

		public int getRequestedTokens() {
			return requestedTokens;
		}

		public Config setRequestedTokens(int requestedTokens) {
			this.requestedTokens = requestedTokens;
			return this;
		}

		@Override
		public String toString() {
			return new ToStringCreator(this).append("replenishRate", replenishRate)
				.append("burstCapacity", burstCapacity)
				.append("requestedTokens", requestedTokens)
				.toString();

		}

	}

}
