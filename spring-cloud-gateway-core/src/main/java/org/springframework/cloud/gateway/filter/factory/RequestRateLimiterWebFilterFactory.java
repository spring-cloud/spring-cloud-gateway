/*
 * Copyright 2013-2017 the original author or authors.
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

import org.springframework.beans.BeansException;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.cloud.gateway.filter.ratelimit.RateLimiter;
import org.springframework.cloud.gateway.filter.ratelimit.RateLimiter.Response;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.http.HttpStatus;
import org.springframework.tuple.Tuple;
import org.springframework.web.server.WebFilter;

import java.util.Arrays;
import java.util.List;

/**
 * User Request Rate Limiter filter.
 * See https://stripe.com/blog/rate-limiters and
 */
public class RequestRateLimiterWebFilterFactory implements WebFilterFactory, ApplicationContextAware {

	public static final String REPLENISH_RATE_KEY = "replenishRate";
	public static final String BURST_CAPACITY_KEY = "burstCapacity";
	public static final String KEY_RESOLVER_NAME_KEY = "keyResolverName";

	private final RateLimiter rateLimiter;
	private ApplicationContext context;

	public RequestRateLimiterWebFilterFactory(RateLimiter rateLimiter) {
		this.rateLimiter = rateLimiter;
	}

	@Override
	public void setApplicationContext(ApplicationContext context) throws BeansException {
		this.context = context;
	}


	@Override
	public List<String> argNames() {
		return Arrays.asList(REPLENISH_RATE_KEY, BURST_CAPACITY_KEY, KEY_RESOLVER_NAME_KEY);
	}

	@SuppressWarnings("unchecked")
	@Override
	public WebFilter apply(Tuple args) {
		// How many requests per second do you want a user to be allowed to do?
		int replenishRate = args.getInt(REPLENISH_RATE_KEY);

		// How much bursting do you want to allow?
		int capacity = args.getInt(BURST_CAPACITY_KEY);

		String beanName = args.getString(KEY_RESOLVER_NAME_KEY);
		KeyResolver keyResolver = this.context.getBean(beanName, KeyResolver.class);

		return (exchange, chain) ->
			keyResolver.resolve(exchange).flatMap(key -> {
				Response response = rateLimiter.isAllowed(key, replenishRate, capacity).block(); //FIXME: block()

				//TODO: set some headers for rate, tokens left

				if (response.isAllowed()) {
					return chain.filter(exchange);
				}
				exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
				return exchange.getResponse().setComplete();
			});
	}

}
