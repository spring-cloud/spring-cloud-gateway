/*
 * Copyright 2017-2019 the original author or authors.
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

package org.springframework.cloud.gateway.filter.quota;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.validation.constraints.Min;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jetbrains.annotations.NotNull;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.springframework.beans.BeansException;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.gateway.route.RouteDefinitionRouteLocator;
import org.springframework.cloud.gateway.support.ConfigurationService;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.util.Assert;
import org.springframework.validation.annotation.Validated;

/**
 * @author Tobias Schug
 */
@ConfigurationProperties("spring.cloud.gateway.redis-quota-filter")
public class RedisQuotaFilter extends AbstractQuotaLimiter<RedisQuotaFilter.Config>
		implements ApplicationContextAware {

	/** Redis Quota Filter property name. */
	public static final String CONFIGURATION_PROPERTY_NAME = "redis-quota-filter";

	/**
	 * Redis Script name.
	 */
	public static final String REDIS_SCRIPT_NAME = "redisRequestQuotaLimiterScript";

	/**
	 * Remaining Quota Rate header name.
	 */
	public static final String REMAINING_HEADER = "X-Quota-Remaining";

	/**
	 * Quota Limit (count) Header name.
	 */
	public static final String QUOTA_LIMIT_HEADER = "X-Quota-Limit";

	/**
	 * Quota Period Header name.
	 */
	public static final String QUOTA_PERIOD_HEADER = "X-Quota-Period";

	private Log log = LogFactory.getLog(getClass());

	private ReactiveStringRedisTemplate redisTemplate;

	private AtomicBoolean initialized = new AtomicBoolean(false);

	private RedisScript<List<Long>> script;

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

	/** The name of the header that returns the quota limit configuration. */
	private String limitHeader = QUOTA_LIMIT_HEADER;

	/** The name of the header that returns the quota period configuration. */
	private String periodHeader = QUOTA_PERIOD_HEADER;

	public RedisQuotaFilter(ReactiveStringRedisTemplate redisTemplate,
			RedisScript<List<Long>> script, ConfigurationService configurationService) {
		super(Config.class, CONFIGURATION_PROPERTY_NAME, configurationService);
		this.redisTemplate = redisTemplate;
		this.script = script;
		this.initialized.compareAndSet(false, true);
	}

	/**
	 * This creates an instance with default static configuration, useful in Java DSL.
	 * @param limit how many tokens per "Period" can be used.
	 * @param period where the limit will be checked, after the period the limit starts at
	 * zero again.
	 */
	public RedisQuotaFilter(int limit, String period) {
		super(Config.class, CONFIGURATION_PROPERTY_NAME, (ConfigurationService) null);
		this.defaultConfig = new Config().setLimit(limit).setPeriod(period);
	}

	static List<String> getKey(String id) {
		// use `{}` around keys to use Redis Key hash tags
		// this allows for using redis cluster

		// Make a unique key per user.
		return Collections
				.singletonList(String.format("request_quota_limiter.{%s}.tokens", id));
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

	String getLimitHeader() {
		return limitHeader;
	}

	void setLimitHeader(String limitHeader) {
		this.limitHeader = limitHeader;
	}

	String getPeriodHeader() {
		return periodHeader;
	}

	void setPeriodHeader(String periodHeader) {
		this.periodHeader = periodHeader;
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
	public Mono<QuotaFilter.Response> isAllowed(String routeId, String id) {
		if (!this.initialized.get()) {
			throw new IllegalStateException("RedisRateLimiter is not initialized");
		}

		Config routeConfig = loadConfiguration(routeId);

		// How many requests do you allow
		int limit = routeConfig.getLimit();
		// How many requests can be done by a period
		QuotaPeriods period = routeConfig.getPeriod();
		// load redis keys
		List<String> keys = getKey(id);
		try {
			// build arugments to pass to the LUA script, time will passed as unixtime in
			// seconds
			Long ttl = -1L; // ABS
			if (period.getTimeUnit().isPresent()) {
				ttl = period.getTimeUnit().get().toSeconds(1);
			}
			List<String> scriptArgs = Arrays.asList(String.valueOf(limit),
					String.valueOf(ttl));

			Flux<List<Long>> redTempExec = this.redisTemplate.execute(this.script, keys,
					scriptArgs);
			return redTempExec
					.onErrorResume(throwable -> Flux.just(Collections.singletonList(-1L)))
					.reduce(new ArrayList<Long>(), (longs, l) -> {
						longs.addAll(l);
						return longs;
					}).map(results -> {
						final Long resultsTokenRemain = results.get(0);
						boolean allowed = resultsTokenRemain >= 0L;
						Long tokensRemaining = resultsTokenRemain >= 0 ? resultsTokenRemain : 0L; //never return a value lower than 0

						QuotaFilter.Response response = new QuotaFilter.Response(allowed,
								getHeaders(routeConfig, tokensRemaining));

						if (log.isDebugEnabled()) {
							log.debug("response: " + response);
						}
						return response;
					});

		}
		catch (Exception e) {
			/*
			 * We don't want a hard dependency on Redis to allow quota traffic.
			 */
			log.error("Error determining if user allowed from redis", e);
		}

		return Mono.just(new QuotaFilter.Response(true, getHeaders(routeConfig, -1L)));
	}

	/* for testing */
	Config loadConfiguration(String routeId) {
		Config routeConfig = getConfig().getOrDefault(routeId, defaultConfig);

		if (routeConfig == null) {
			routeConfig = getConfig().get(RouteDefinitionRouteLocator.DEFAULT_FILTERS);
		}

		if (routeConfig == null) {
			throw new IllegalArgumentException(
					"No Configuration found for route " + routeId + " or defaultFilters");
		}
		return routeConfig;
	}

	@NotNull
	public Map<String, String> getHeaders(Config config, Long tokensLeft) {
		Map<String, String> headers = new HashMap<>();
		if (isIncludeHeaders()) {
			headers.put(this.remainingHeader, tokensLeft.toString());
			headers.put(this.limitHeader, String.valueOf(config.getLimit()));
			headers.put(this.periodHeader, String.valueOf(config.getPeriod()));
		}
		return headers;
	}

	@Validated
	public static class Config {

		@Min(1)
		private int limit;

		/* SECOND, MINUTE, DAY, YEAR, ABS */
		private QuotaPeriods period = QuotaPeriods.SECONDS;

		public int getLimit() {
			return limit;
		}

		public Config setLimit(int limit) {
			this.limit = limit;
			return this;
		}

		public QuotaPeriods getPeriod() {
			return period;
		}

		public Config setPeriod(String period) {
			QuotaPeriods quotaPeriods = QuotaPeriods.fromStringWithDefault(period);
			// Assert thats not null
			Assert.notNull(quotaPeriods,
					"The period is wrong, it has to be [ "
							+ QuotaPeriods.SECONDS.timeUnitName + ", "
							+ QuotaPeriods.MINUTES.timeUnitName + ", "
							+ QuotaPeriods.HOURS.timeUnitName + ", "
							+ QuotaPeriods.DAYS.timeUnitName + " ]");
			this.period = quotaPeriods;
			return this;
		}

		@Override
		public String toString() {
			return "Config{" + "limit=" + limit + ", period=" + period + '}';
		}

	}

	/**
	 * QuotaFilter time periods.
	 */
	@SuppressWarnings("unchecked")
	public enum QuotaPeriods {

		/**
		 * Available Timeunits for creating time bases quotas.
		 */
		SECONDS(TimeUnit.SECONDS), MINUTES(TimeUnit.MINUTES), HOURS(TimeUnit.HOURS), DAYS(
				TimeUnit.DAYS),
		/**
		 * ABS have no timeunit, we have vo handle null here.
		 */
		ABS(null);

		private TimeUnit timeUnit;

		private String timeUnitName;

		QuotaPeriods(TimeUnit timeUnit) {
			this.timeUnit = timeUnit;
			if (this.timeUnit == null) {
				this.timeUnitName = "ABS";
			}
			else {
				this.timeUnitName = timeUnit.name();
			}
		}

		Optional<TimeUnit> getTimeUnit() {
			return Optional.ofNullable(this.timeUnit);
		}

		public String getTimeUnitName() {
			return this.timeUnitName;
		}

		@Override
		public String toString() {
			return this.timeUnitName;
		}

		/*
		 * look for description in enums, return seconds as default.
		 */
		public static QuotaPeriods fromStringWithDefault(String value) {
			for (QuotaPeriods quotaPeriod : QuotaPeriods.values()) {
				if (quotaPeriod.timeUnitName.equalsIgnoreCase(value)) {
					return quotaPeriod;
				}
			}
			return null;
		}

	}

}
