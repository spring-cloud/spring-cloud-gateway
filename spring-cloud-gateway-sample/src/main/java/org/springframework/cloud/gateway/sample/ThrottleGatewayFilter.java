/*
 * Copyright 2013-2019 the original author or authors.
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

package org.springframework.cloud.gateway.sample;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import com.google.common.collect.Maps;
import com.google.common.hash.BloomFilter;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.isomorphism.util.TokenBucket;
import org.isomorphism.util.TokenBuckets;
import reactor.core.publisher.Mono;

import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ServerWebExchange;

/**
 * Sample throttling filter. See https://github.com/bbeck/token-bucket
 */
public class ThrottleGatewayFilter implements GatewayFilter {

	private static final Log log = LogFactory.getLog(ThrottleGatewayFilter.class);

	private int capacity;

	private int refillTokens;

	private int refillPeriod;

	private TimeUnit refillUnit;

	private Map<String, TokenBucket> stringTokenBucketMap = Maps.newConcurrentMap();

	public int getCapacity() {
		return capacity;
	}

	public ThrottleGatewayFilter setCapacity(int capacity) {
		this.capacity = capacity;
		return this;
	}

	public int getRefillTokens() {
		return refillTokens;
	}

	public ThrottleGatewayFilter setRefillTokens(int refillTokens) {
		this.refillTokens = refillTokens;
		return this;
	}

	public int getRefillPeriod() {
		return refillPeriod;
	}

	public ThrottleGatewayFilter setRefillPeriod(int refillPeriod) {
		this.refillPeriod = refillPeriod;
		return this;
	}

	public TimeUnit getRefillUnit() {
		return refillUnit;
	}

	public ThrottleGatewayFilter setRefillUnit(TimeUnit refillUnit) {
		this.refillUnit = refillUnit;
		return this;
	}

	@Override
	public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
		String fullAddress = exchange.getRequest().getRemoteAddress().toString();
		String ip = fullAddress.substring(1, fullAddress.indexOf(":"));
		synchronized (this) {
			if (!stringTokenBucketMap.containsKey(ip)) {
				TokenBucket tokenBucket = TokenBuckets.builder().withCapacity(capacity)
						.withFixedIntervalRefillStrategy(refillTokens, refillPeriod, refillUnit)
						.build();
				stringTokenBucketMap.put(ip, tokenBucket);
			}
		}
		//get a token bucket for ip
		TokenBucket tokenBucket = stringTokenBucketMap.get(ip);
		log.debug("TokenBucket capacity: " + tokenBucket.getCapacity());
		boolean consumed = tokenBucket.tryConsume();
		if (consumed) {
			return chain.filter(exchange);
		}
		exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
		return exchange.getResponse().setComplete();
	}

}
