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

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.springframework.cloud.gateway.filter.factory.cache.CachedResponse;
import org.springframework.web.server.ServerWebExchange;

public class SetCacheDirectivesByMaxAgeAfterCacheExchangeMutator implements AfterCacheExchangeMutator {

	final Pattern MAX_AGE_PATTERN = Pattern.compile("(?:,|^)\\s*max-age=(\\d+)");

	@Override
	public void accept(ServerWebExchange exchange, CachedResponse cachedResponse) {
		Optional<Integer> maxAge = Optional.ofNullable(exchange.getResponse().getHeaders().getCacheControl())
			.map(MAX_AGE_PATTERN::matcher)
			.filter(Matcher::find)
			.map(matcher -> matcher.group(1))
			.map(Integer::parseInt);

		if (maxAge.isPresent()) {
			if (maxAge.get() > 0) {
				removeNoCacheHeaders(exchange);
			}
			else {
				keepNoCacheHeaders(exchange);
			}
		}
	}

	private void keepNoCacheHeaders(ServerWebExchange exchange) {
		// at least it contains 'max-age' so we can append items with commas safely
		String cacheControl = exchange.getResponse().getHeaders().getCacheControl();
		StringBuilder newCacheControl = new StringBuilder(cacheControl);

		if (!cacheControl.contains("no-cache")) {
			newCacheControl.append(",no-cache");
		}

		if (!cacheControl.contains("must-revalidate")) {
			newCacheControl.append(",must-revalidate");
		}
		exchange.getResponse().getHeaders().setCacheControl(newCacheControl.toString());
	}

	private void removeNoCacheHeaders(ServerWebExchange exchange) {
		String cacheControl = exchange.getResponse().getHeaders().getCacheControl();
		List<String> cacheControlValues = Arrays.asList(cacheControl.split("\\s*,\\s*"));

		String newCacheControl = cacheControlValues.stream()
			.filter(s -> !s.matches("must-revalidate|no-cache|no-store"))
			.collect(Collectors.joining(","));
		exchange.getResponse().getHeaders().setCacheControl(newCacheControl);
	}

}
