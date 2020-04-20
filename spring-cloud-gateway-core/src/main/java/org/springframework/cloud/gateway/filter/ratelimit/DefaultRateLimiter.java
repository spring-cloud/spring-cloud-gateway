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

package org.springframework.cloud.gateway.filter.ratelimit;

import java.util.concurrent.atomic.AtomicBoolean;

import javax.validation.constraints.Min;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import reactor.core.publisher.Mono;

import org.springframework.beans.BeansException;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.gateway.support.ConfigurationService;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.core.style.ToStringCreator;
import org.springframework.validation.annotation.Validated;

/**
 * @author Emmanouil Gkatziouras
 */
@ConfigurationProperties("spring.cloud.gateway.default-rate-limiter")
public class DefaultRateLimiter extends AbstractRateLimiter<DefaultRateLimiter.Config>
		implements ApplicationContextAware {

	/**
	 * Default Rate Limiter property name.
	 */
	public static final String CONFIGURATION_PROPERTY_NAME = "default-rate-limiter";

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

	private AtomicBoolean initialized = new AtomicBoolean(false);

	private Config defaultConfig;

	public DefaultRateLimiter(ConfigurationService configurationService) {
		super(Config.class, CONFIGURATION_PROPERTY_NAME, configurationService);
		this.initialized.compareAndSet(false, true);
	}

	/**
	 * This creates an instance with default static configuration, useful in Java DSL.
	 * @param defaultReplenishRate how many tokens per second in token-bucket algorithm.
	 * @param defaultBurstCapacity how many tokens the bucket can hold in token-bucket
	 * algorithm.
	 */
	public DefaultRateLimiter(int defaultReplenishRate, int defaultBurstCapacity) {
		super(Config.class, CONFIGURATION_PROPERTY_NAME, (ConfigurationService) null);
		this.defaultConfig = new Config().setReplenishRate(defaultReplenishRate)
				.setBurstCapacity(defaultBurstCapacity);
	}

	/**
	 * This creates an instance with default static configuration, useful in Java DSL.
	 * @param defaultReplenishRate how many tokens per second in token-bucket algorithm.
	 * @param defaultBurstCapacity how many tokens the bucket can hold in token-bucket
	 * algorithm.
	 * @param defaultRequestedTokens how many tokens are requested per request.
	 */
	public DefaultRateLimiter(int defaultReplenishRate, int defaultBurstCapacity,
			int defaultRequestedTokens) {
		this(defaultReplenishRate, defaultBurstCapacity);
		this.defaultConfig.setRequestedTokens(defaultRequestedTokens);
	}

	@Override
	public Mono<Response> isAllowed(String routeId, String id) {
		return null;
	}

	/**
	 * Used when setting default configuration in constructor.
	 * @param context the ApplicationContext object to be used by this object
	 * @throws BeansException if thrown by application context methods
	 */
	@Override
	@SuppressWarnings("unchecked")
	public void setApplicationContext(ApplicationContext context) throws BeansException {
		if(initialized.compareAndSet(false,true)) {
			if (context.getBeanNamesForType(ConfigurationService.class).length > 0) {
				setConfigurationService(context.getBean(ConfigurationService.class));
			}
		}
	}

	@Validated
	public static class Config {

		@Min(1)
		private int replenishRate;

		@Min(1)
		private int burstCapacity = 1;

		@Min(1)
		private int requestedTokens = 1;


		public int getReplenishRate() {
			return replenishRate;
		}

		public DefaultRateLimiter.Config setReplenishRate(int replenishRate) {
			this.replenishRate = replenishRate;
			return this;
		}

		public int getBurstCapacity() {
			return burstCapacity;
		}

		public DefaultRateLimiter.Config setBurstCapacity(int burstCapacity) {
			this.burstCapacity = burstCapacity;
			return this;
		}

		public int getRequestedTokens() {
			return requestedTokens;
		}

		public DefaultRateLimiter.Config setRequestedTokens(int requestedTokens) {
			this.requestedTokens = requestedTokens;
			return this;
		}

		@Override
		public String toString() {
			return new ToStringCreator(this).append("replenishRate", replenishRate)
					.append("burstCapacity", burstCapacity)
					.append("requestedTokens", requestedTokens).toString();

		}

	}
}
