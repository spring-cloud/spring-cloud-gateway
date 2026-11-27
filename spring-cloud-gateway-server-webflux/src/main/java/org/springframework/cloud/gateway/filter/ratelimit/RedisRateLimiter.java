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

package org.springframework.cloud.gateway.filter.ratelimit;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

import jakarta.validation.constraints.Min;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jspecify.annotations.Nullable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.springframework.beans.BeansException;
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
 * @author Andrey Muchnik
 */
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

	/**
	 * Constant representing the maximum number that can be safely used in Redis scripts.
	 * This is the largest number that can be represented accurately in Java's `long` type
	 * and is equal to 2^53 - 1, which is the largest number that Lua can reliably handle.
	 */
	private static final Long REDIS_LUA_MAX_SAFE_INTEGER = 9007199254740991L;

	private Log log = LogFactory.getLog(getClass());

	private @Nullable ReactiveStringRedisTemplate redisTemplate;

	private @Nullable RedisScript<List<Long>> script;

	private AtomicBoolean initialized = new AtomicBoolean(false);

	private @Nullable Config defaultConfig;

	private final RedisRateLimiterProperties properties;

	public RedisRateLimiter(ReactiveStringRedisTemplate redisTemplate, RedisScript<List<Long>> script,
			ConfigurationService configurationService) {
		this(redisTemplate, script, configurationService, new RedisRateLimiterProperties());
	}

	public RedisRateLimiter(ReactiveStringRedisTemplate redisTemplate, RedisScript<List<Long>> script,
			ConfigurationService configurationService, RedisRateLimiterProperties properties) {
		super(Config.class, CONFIGURATION_PROPERTY_NAME, configurationService);
		this.redisTemplate = redisTemplate;
		this.script = script;
		this.properties = properties;
		this.initialized.compareAndSet(false, true);
	}

	/**
	 * This creates an instance with default static configuration, useful in Java DSL.
	 * @param defaultReplenishRate how many tokens per second in token-bucket algorithm.
	 * @param defaultBurstCapacity how many tokens the bucket can hold in token-bucket
	 * algorithm.
	 */
	public RedisRateLimiter(int defaultReplenishRate, long defaultBurstCapacity) {
		super(Config.class, CONFIGURATION_PROPERTY_NAME, (ConfigurationService) null);
		this.properties = new RedisRateLimiterProperties();
		this.defaultConfig = new Config().setReplenishRate(defaultReplenishRate).setBurstCapacity(defaultBurstCapacity);
	}

	/**
	 * This creates an instance with default static configuration, useful in Java DSL.
	 * @param defaultReplenishRate how many tokens per second in token-bucket algorithm.
	 * @param defaultBurstCapacity how many tokens the bucket can hold in token-bucket
	 * algorithm.
	 * @param defaultRequestedTokens how many tokens are requested per request.
	 */
	public RedisRateLimiter(int defaultReplenishRate, long defaultBurstCapacity, int defaultRequestedTokens) {
		this(defaultReplenishRate, defaultBurstCapacity);
		Objects.requireNonNull(this.defaultConfig, "defaultConfig may not be null");
		this.defaultConfig.setRequestedTokens(defaultRequestedTokens);
	}

	static List<String> getKeys(String id, String routeId) {
		// use `{}` around keys to use Redis Key hash tags
		// this allows for using redis cluster

		// Make a unique key per user and route.
		String prefix = "request_rate_limiter.{" + routeId + "." + id + "}.";

		// You need two Redis keys for Token Bucket.
		String tokenKey = prefix + "tokens";
		String timestampKey = prefix + "timestamp";
		return Arrays.asList(tokenKey, timestampKey);
	}

	/**
	 * The externalized rate limiter properties bound from configuration.
	 * @return the properties backing this rate limiter
	 */
	public RedisRateLimiterProperties getProperties() {
		return properties;
	}

	/**
	 * The per-route configuration is held by {@link RedisRateLimiterProperties} so that
	 * {@code redis-rate-limiter.config.*} is bound onto the properties bean, which is
	 * where the binding now lives. Reading through to it keeps a single source of truth
	 * and picks up any rebinding on refresh.
	 */
	@Override
	public Map<String, Config> getConfig() {
		return properties.getConfig();
	}

	/**
	 * @return whether rate limiter headers are included
	 * @deprecated in favor of {@link RedisRateLimiterProperties#isIncludeHeaders()} via
	 * {@link #getProperties()}
	 */
	@Deprecated(since = "5.0.3")
	public boolean isIncludeHeaders() {
		return properties.isIncludeHeaders();
	}

	/**
	 * @param includeHeaders whether rate limiter headers are included
	 * @deprecated in favor of
	 * {@link RedisRateLimiterProperties#setIncludeHeaders(boolean)} via
	 * {@link #getProperties()}
	 */
	@Deprecated(since = "5.0.3")
	public void setIncludeHeaders(boolean includeHeaders) {
		properties.setIncludeHeaders(includeHeaders);
	}

	/**
	 * @return the remaining-requests header name
	 * @deprecated in favor of {@link RedisRateLimiterProperties#getRemainingHeader()} via
	 * {@link #getProperties()}
	 */
	@Deprecated(since = "5.0.3")
	public String getRemainingHeader() {
		return properties.getRemainingHeader();
	}

	/**
	 * @param remainingHeader the remaining-requests header name
	 * @deprecated in favor of
	 * {@link RedisRateLimiterProperties#setRemainingHeader(String)} via
	 * {@link #getProperties()}
	 */
	@Deprecated(since = "5.0.3")
	public void setRemainingHeader(String remainingHeader) {
		properties.setRemainingHeader(remainingHeader);
	}

	/**
	 * @return the replenish-rate header name
	 * @deprecated in favor of {@link RedisRateLimiterProperties#getReplenishRateHeader()}
	 * via {@link #getProperties()}
	 */
	@Deprecated(since = "5.0.3")
	public String getReplenishRateHeader() {
		return properties.getReplenishRateHeader();
	}

	/**
	 * @param replenishRateHeader the replenish-rate header name
	 * @deprecated in favor of
	 * {@link RedisRateLimiterProperties#setReplenishRateHeader(String)} via
	 * {@link #getProperties()}
	 */
	@Deprecated(since = "5.0.3")
	public void setReplenishRateHeader(String replenishRateHeader) {
		properties.setReplenishRateHeader(replenishRateHeader);
	}

	/**
	 * @return the burst-capacity header name
	 * @deprecated in favor of {@link RedisRateLimiterProperties#getBurstCapacityHeader()}
	 * via {@link #getProperties()}
	 */
	@Deprecated(since = "5.0.3")
	public String getBurstCapacityHeader() {
		return properties.getBurstCapacityHeader();
	}

	/**
	 * @param burstCapacityHeader the burst-capacity header name
	 * @deprecated in favor of
	 * {@link RedisRateLimiterProperties#setBurstCapacityHeader(String)} via
	 * {@link #getProperties()}
	 */
	@Deprecated(since = "5.0.3")
	public void setBurstCapacityHeader(String burstCapacityHeader) {
		properties.setBurstCapacityHeader(burstCapacityHeader);
	}

	/**
	 * @return the requested-tokens header name
	 * @deprecated in favor of
	 * {@link RedisRateLimiterProperties#getRequestedTokensHeader()} via
	 * {@link #getProperties()}
	 */
	@Deprecated(since = "5.0.3")
	public String getRequestedTokensHeader() {
		return properties.getRequestedTokensHeader();
	}

	/**
	 * @param requestedTokensHeader the requested-tokens header name
	 * @deprecated in favor of
	 * {@link RedisRateLimiterProperties#setRequestedTokensHeader(String)} via
	 * {@link #getProperties()}
	 */
	@Deprecated(since = "5.0.3")
	public void setRequestedTokensHeader(String requestedTokensHeader) {
		properties.setRequestedTokensHeader(requestedTokensHeader);
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

	@SuppressWarnings("NullAway")
	/* for testing */
	Config getDefaultConfig() {
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
		long burstCapacity = routeConfig.getBurstCapacity();

		// How many tokens are requested per request?
		int requestedTokens = routeConfig.getRequestedTokens();

		try {
			List<String> keys = getKeys(id, routeId);

			// The arguments to the LUA script. time() returns unixtime in seconds.
			List<String> scriptArgs = Arrays.asList(replenishRate + "", burstCapacity + "", "", requestedTokens + "");
			// allowed, tokens_left = redis.eval(SCRIPT, keys, args)
			Objects.requireNonNull(this.redisTemplate, "redisTemplate may not be null");
			Objects.requireNonNull(this.script, "script may not be null");
			Flux<List<Long>> flux = this.redisTemplate.execute(this.script, keys, scriptArgs);
			// .log("redisratelimiter", Level.FINER);
			return flux.onErrorResume(throwable -> {
				log.error("Error calling rate limiter lua", throwable);
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

	@SuppressWarnings("NullAway")
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
		if (properties.isIncludeHeaders()) {
			headers.put(properties.getRemainingHeader(), tokensLeft.toString());
			headers.put(properties.getReplenishRateHeader(), String.valueOf(config.getReplenishRate()));
			headers.put(properties.getBurstCapacityHeader(), String.valueOf(config.getBurstCapacity()));
			headers.put(properties.getRequestedTokensHeader(), String.valueOf(config.getRequestedTokens()));
		}
		return headers;
	}

	@Validated
	public static class Config {

		@Min(1)
		private int replenishRate;

		@Min(0)
		private long burstCapacity = 1;

		@Min(1)
		private int requestedTokens = 1;

		public int getReplenishRate() {
			return replenishRate;
		}

		public Config setReplenishRate(int replenishRate) {
			this.replenishRate = replenishRate;
			return this;
		}

		public long getBurstCapacity() {
			return burstCapacity;
		}

		public Config setBurstCapacity(long burstCapacity) {
			Assert.isTrue(burstCapacity >= this.replenishRate, "BurstCapacity(" + burstCapacity
					+ ") must be greater than or equal than replenishRate(" + this.replenishRate + ")");
			Assert.isTrue(burstCapacity <= REDIS_LUA_MAX_SAFE_INTEGER, "BurstCapacity(" + burstCapacity
					+ ") must not exceed the maximum allowed value of " + REDIS_LUA_MAX_SAFE_INTEGER);
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
