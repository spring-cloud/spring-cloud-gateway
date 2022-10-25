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

package org.springframework.cloud.gateway.filter.factory.cache.postprocessor;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.cloud.gateway.filter.factory.cache.CachedResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.server.ServerWebExchange;

/**
 * It sets the {@link HttpHeaders#CACHE_CONTROL} {@literal max-age} value. The value is
 * calculated taking the {@link #configuredTimeToLive} cache configuration and the age of
 * the entry.
 *
 * @author Marta Medio
 * @author Ignacio Lozano
 */
public class SetMaxAgeHeaderAfterCacheExchangeMutator implements AfterCacheExchangeMutator {

	private static final String MAX_AGE_PREFIX = "max-age=";

	private final Duration configuredTimeToLive;

	private final Clock clock;

	public SetMaxAgeHeaderAfterCacheExchangeMutator(Duration configuredTimeToLive, Clock clock) {
		this.configuredTimeToLive = configuredTimeToLive;
		this.clock = clock;
	}

	@Override
	public void accept(ServerWebExchange exchange, CachedResponse cachedResponse) {
		ServerHttpResponse response = exchange.getResponse();
		long calculatedMaxAgeInSeconds = calculateMaxAgeInSeconds(cachedResponse, configuredTimeToLive);
		rewriteCacheControlMaxAge(response.getHeaders(), calculatedMaxAgeInSeconds);
	}

	private long calculateMaxAgeInSeconds(CachedResponse cachedResponse, Duration configuredTimeToLive) {
		long maxAge;
		if (configuredTimeToLive.getSeconds() == -1) {
			maxAge = -1;
		}
		else {
			maxAge = Math.max(0, configuredTimeToLive.minus(getElapsedTimeInSeconds(cachedResponse)).getSeconds());
		}

		return maxAge;
	}

	private Duration getElapsedTimeInSeconds(CachedResponse cachedResponse) {
		return Duration.ofMillis(clock.millis() - cachedResponse.timestamp().getTime());
	}

	private static void rewriteCacheControlMaxAge(HttpHeaders headers, long seconds) {
		boolean isMaxAgePresent = headers.getCacheControl() != null
				&& headers.getCacheControl().contains(MAX_AGE_PREFIX);

		if (isMaxAgePresent) {
			List<String> cacheControlHeaders = headers.get(HttpHeaders.CACHE_CONTROL);
			cacheControlHeaders = cacheControlHeaders == null ? Collections.emptyList() : cacheControlHeaders;
			List<String> replacedCacheControlHeaders = new ArrayList<>();
			for (String value : cacheControlHeaders) {
				if (value.contains(MAX_AGE_PREFIX)) {
					if (seconds == -1) {
						List<String> removedMaxAgeList = Arrays.stream(value.split(","))
								.filter(i -> !i.trim().startsWith(MAX_AGE_PREFIX)).collect(Collectors.toList());
						value = String.join(",", removedMaxAgeList);
					}
					else {
						value = value.replaceFirst("\\bmax-age=\\d+\\b", MAX_AGE_PREFIX + seconds);
					}
				}
				replacedCacheControlHeaders.add(value);
			}
			headers.remove(HttpHeaders.CACHE_CONTROL);
			headers.addAll(HttpHeaders.CACHE_CONTROL, replacedCacheControlHeaders);
		}
	}

}
