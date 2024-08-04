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

package org.springframework.cloud.gateway.filter.factory;

import java.util.Collections;
import java.util.Map;

import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.cloud.gateway.filter.ratelimit.RateLimiter;
import org.springframework.cloud.gateway.filter.ratelimit.RateLimiter.Response;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.cloud.gateway.test.BaseWebClientTests;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.test.annotation.DirtiesContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

/**
 * see
 * https://gist.github.com/ptarjan/e38f45f2dfe601419ca3af937fff574d#file-1-check_request_rate_limiter-rb-L36-L62
 *
 * @author Spencer Gibb
 */
@SpringBootTest(webEnvironment = RANDOM_PORT)
@DirtiesContext
public class RequestRateLimiterGatewayFilterFactoryTests extends BaseWebClientTests {

	@Autowired
	@Qualifier("resolver2")
	KeyResolver resolver2;

	@Autowired
	private ApplicationContext context;

	@MockBean
	private RateLimiter rateLimiter;

	@MockBean
	private GatewayFilterChain filterChain;

	@Test
	public void allowedWorks() {
		// tests that auto wired as default works
		assertFilterFactory(null, "allowedkey", true, HttpStatus.OK);
	}

	@Test
	public void notAllowedWorks() {
		assertFilterFactory(resolver2, "notallowedkey", false, HttpStatus.TOO_MANY_REQUESTS);
	}

	@Test
	public void emptyKeyDenied() {
		assertFilterFactory(exchange -> Mono.empty(), null, true, HttpStatus.FORBIDDEN);
	}

	@Test
	public void emptyKeyAllowed() {
		assertFilterFactory(exchange -> Mono.empty(), null, true, HttpStatus.OK, false);
	}

	private void assertFilterFactory(KeyResolver keyResolver, String key, boolean allowed, HttpStatus expectedStatus) {
		assertFilterFactory(keyResolver, key, allowed, expectedStatus, null);
	}

	private void assertFilterFactory(KeyResolver keyResolver, String key, boolean allowed, HttpStatus expectedStatus,
			Boolean denyEmptyKey) {

		String tokensRemaining = allowed ? "1" : "0";

		Map<String, String> headers = Collections.singletonMap("X-Tokens-Remaining", tokensRemaining);

		if (key != null) {
			when(rateLimiter.isAllowed("myroute", key)).thenReturn(Mono.just(new Response(allowed, headers)));
		}

		MockServerHttpRequest request = MockServerHttpRequest.get("/").build();
		MockServerWebExchange exchange = MockServerWebExchange.from(request);
		exchange.getResponse().setStatusCode(HttpStatus.OK);
		exchange.getAttributes()
			.put(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR,
					Route.async().id("myroute").predicate(ex -> true).uri("http://localhost").build());

		when(this.filterChain.filter(exchange)).thenReturn(Mono.empty());

		RequestRateLimiterGatewayFilterFactory factory = this.context
			.getBean(RequestRateLimiterGatewayFilterFactory.class);
		if (denyEmptyKey != null) {
			factory.setDenyEmptyKey(denyEmptyKey);
		}
		GatewayFilter filter = factory.apply(config -> {
			config.setRouteId("myroute");
			config.setKeyResolver(keyResolver);
		});

		Mono<Void> response = filter.filter(exchange, this.filterChain);
		response.subscribe(aVoid -> {
			assertThat(exchange.getResponse().getStatusCode()).isEqualTo(expectedStatus);
			assertThat(exchange.getResponse().getHeaders()).containsEntry("X-Tokens-Remaining",
					Collections.singletonList(tokensRemaining));
		});

	}

	@EnableAutoConfiguration
	@SpringBootConfiguration
	@Import(BaseWebClientTests.DefaultTestConfig.class)
	public static class TestConfig {

		@Bean
		@Primary
		KeyResolver resolver1() {
			return exchange -> Mono.just("allowedkey");
		}

		@Bean
		KeyResolver resolver2() {
			return exchange -> Mono.just("notallowedkey");
		}

	}

}
