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

import org.springframework.cloud.gateway.filter.ratelimit.RateLimiter;
import org.springframework.cloud.gateway.filter.ratelimit.RateLimiter.Response;
import org.springframework.http.HttpStatus;
import org.springframework.tuple.Tuple;
import org.springframework.web.server.WebFilter;

/**
 * User Request Rate Limiter filter.
 * See https://stripe.com/blog/rate-limiters and
 */
public class RequestRateLimiterWebFilterFactory implements WebFilterFactory {

	private final RateLimiter rateLimiter;

	public RequestRateLimiterWebFilterFactory(RateLimiter rateLimiter) {
		this.rateLimiter = rateLimiter;
	}

	@SuppressWarnings("unchecked")
	@Override
	public WebFilter apply(Tuple args) {
		// How many requests per second do you want a user to be allowed to do?
		int replenishRate = 100;

		// How much bursting do you want to allow?
		int capacity = 5 * replenishRate;

		return (exchange, chain) -> {
			// exchange.getPrincipal().flatMap(principal -> {})
			//TODO: get user from request, maybe a KeyResolutionStrategy.resolve(exchange). Lookup strategy bean via arg
			Response response = rateLimiter.isAllowed("me", replenishRate, capacity);

			//TODO: set some headers for rate, tokens left

			if (response.isAllowed()) {
				return chain.filter(exchange);
			}
			exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
			return exchange.getResponse().setComplete();
		};
	}

}
