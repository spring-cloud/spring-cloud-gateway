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

import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.cloud.gateway.filter.ratelimit.RateLimiter;
import org.springframework.http.HttpStatus;
import org.springframework.tuple.Tuple;

/**
 * User Request Rate Limiter filter. See https://stripe.com/blog/rate-limiters and
 */
public class RequestRateLimiterGatewayFilterFactory implements GatewayFilterFactory {

	public static final String KEY_RESOLVER_KEY = "keyResolver";

	private final RateLimiter rateLimiter;
	private final KeyResolver defaultKeyResolver;

	public RequestRateLimiterGatewayFilterFactory(RateLimiter rateLimiter,
			KeyResolver defaultKeyResolver) {
		this.rateLimiter = rateLimiter;
		this.defaultKeyResolver = defaultKeyResolver;
	}

	public KeyResolver getDefaultKeyResolver() {
		return defaultKeyResolver;
	}

	@SuppressWarnings("unchecked")
	@Override
	public GatewayFilter apply(Tuple args) {

		KeyResolver keyResolver;
		if (args.hasFieldName(KEY_RESOLVER_KEY)) {
			keyResolver = args.getValue(KEY_RESOLVER_KEY, KeyResolver.class);
		} else {
			keyResolver = defaultKeyResolver;
		}
		return apply(keyResolver, args);
	}

	public GatewayFilter apply(KeyResolver keyResolver, Tuple args) {

		return (exchange, chain) -> keyResolver.resolve(exchange).flatMap(key ->
		// TODO: if key is empty?
		rateLimiter.isAllowed(key, args).flatMap(response -> {
			// TODO: set some headers for rate, tokens left

			if (response.isAllowed()) {
				return chain.filter(exchange);
			}
			exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
			return exchange.getResponse().setComplete();
		}));
	}

}
