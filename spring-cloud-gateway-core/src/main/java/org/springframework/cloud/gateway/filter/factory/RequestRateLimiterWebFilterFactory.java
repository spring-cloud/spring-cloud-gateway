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

import java.time.Instant;
import java.util.Arrays;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.tuple.Tuple;
import org.springframework.web.server.WebFilter;

/**
 * Sample User Request Rate Throttle filter.
 * See https://stripe.com/blog/rate-limiters and
 * https://gist.github.com/ptarjan/e38f45f2dfe601419ca3af937fff574d#file-1-check_request_rate_limiter-rb-L11-L34
 */
public class RequestRateLimiterWebFilterFactory implements WebFilterFactory {
	private Log log = LogFactory.getLog(getClass());

	private final StringRedisTemplate redisTemplate;
	private final RedisScript<List> script;

	public RequestRateLimiterWebFilterFactory(StringRedisTemplate redisTemplate, RedisScript<List> script) {
		this.redisTemplate = redisTemplate;
		this.script = script;
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
			boolean allowed = isAllowed(replenishRate, capacity, "me");

			if (allowed) {
				return chain.filter(exchange);
			}
			exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
			return exchange.getResponse().setComplete();
		};
	}

	//TODO: move to interface
	//TODO: use tuple args except for id
	/* for testing */ boolean isAllowed(int replenishRate, int capacity, String id) {
		boolean allowed = false;

		try {
			// # Make a unique key per user.
			String prefix = "request_rate_limiter." + id;

			// # You need two Redis keys for Token Bucket.
			List<String> keys = Arrays.asList(prefix + ".tokens", prefix + ".timestamp");

			// The arguments to the LUA script. time() returns unixtime in seconds.
			String[] args = new String[]{ replenishRate+"", capacity+"", Instant.now().getEpochSecond()+"", "1"};
			// allowed, tokens_left = redis.eval(SCRIPT, keys, args)
			List results = this.redisTemplate.execute(this.script, keys, args);

			allowed = new Long(1L).equals(results.get(0));
			Long tokensLeft = (Long) results.get(1);

			if (log.isDebugEnabled()) {
				log.debug("isAllowed("+id+")=" + allowed + ", tokensLeft: "+tokensLeft);
			}

		} catch (Exception e) {
			log.error("Error determining if user allowed from redis", e);
		}
		return allowed;
	}
}
