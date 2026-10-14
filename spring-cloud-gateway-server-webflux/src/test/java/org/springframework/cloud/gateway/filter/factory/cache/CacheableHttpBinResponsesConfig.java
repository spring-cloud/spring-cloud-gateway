/*
 * Copyright © 2013 Broadcom Inc. and/or its subsidiaries. All Rights Reserved.
 * Copyright 2013-present the original author or authors.
 */

package org.springframework.cloud.gateway.filter.factory.cache;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import reactor.core.publisher.Mono;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.util.StringUtils;
import org.springframework.web.server.WebFilter;

/**
 * Spring Security's default response headers include
 * {@code Cache-Control: no-cache, no-store, max-age=0, must-revalidate}, which a
 * response cache must not store. The local response cache tests use the in-process
 * httpbin as their upstream, so this removes the {@code no-store} and {@code private}
 * directives from its responses, keeping the rest of the headers (including
 * {@code Pragma} and {@code Expires}) as Spring Security wrote them.
 */
@TestConfiguration(proxyBeanMethods = false)
class CacheableHttpBinResponsesConfig {

	private static final Set<String> FORBIDDEN_DIRECTIVES = Set.of("no-store", "private");

	@Bean
	@Order(Ordered.LOWEST_PRECEDENCE)
	WebFilter cacheableHttpBinResponsesFilter() {
		return (exchange, chain) -> {
			if (exchange.getRequest().getPath().value().startsWith("/httpbin")) {
				// registered after Spring Security's header writer, so it runs after it
				exchange.getResponse().beforeCommit(() -> {
					HttpHeaders headers = exchange.getResponse().getHeaders();
					List<String> values = headers.get(HttpHeaders.CACHE_CONTROL);
					if (values != null) {
						String cacheable = values.stream()
							.flatMap(value -> Arrays.stream(StringUtils.tokenizeToStringArray(value, ",")))
							.filter(directive -> !FORBIDDEN_DIRECTIVES.contains(directive.toLowerCase(Locale.ROOT)))
							.collect(Collectors.joining(", "));
						headers.setCacheControl(cacheable);
					}
					return Mono.empty();
				});
			}
			return chain.filter(exchange);
		};
	}

}
