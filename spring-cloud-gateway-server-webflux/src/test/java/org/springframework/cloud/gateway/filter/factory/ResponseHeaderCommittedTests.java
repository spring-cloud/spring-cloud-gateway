/*
 * Copyright 2013-present the original author or authors.
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

import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.cloud.gateway.test.BaseWebClientTests;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.web.server.ServerWebExchange;

import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

/**
 * Tests for response header filters when response is already committed. This verifies the
 * fix for issue #3718 where filters like RequestRateLimiter commit the response, but
 * post-processing response header filters still try to modify headers.
 *
 * @author issue #3718
 */
@SpringBootTest(webEnvironment = RANDOM_PORT)
@DirtiesContext
class ResponseHeaderCommittedTests extends BaseWebClientTests {

	@Test
	void setResponseHeaderShouldNotThrowWhenResponseCommitted() {
		testClient.get()
			.uri("/committed")
			.header("Host", "www.set-committed.org")
			.exchange()
			.expectStatus()
			.isEqualTo(HttpStatus.FORBIDDEN)
			.expectHeader()
			.doesNotExist("X-Custom-Header");
	}

	@Test
	void removeResponseHeaderShouldNotThrowWhenResponseCommitted() {
		testClient.get()
			.uri("/committed")
			.header("Host", "www.remove-committed.org")
			.exchange()
			.expectStatus()
			.isEqualTo(HttpStatus.FORBIDDEN);
	}

	@Test
	void dedupeResponseHeaderShouldNotThrowWhenResponseCommitted() {
		testClient.get()
			.uri("/committed")
			.header("Host", "www.dedupe-committed.org")
			.exchange()
			.expectStatus()
			.isEqualTo(HttpStatus.FORBIDDEN);
	}

	@Test
	void rewriteResponseHeaderShouldNotThrowWhenResponseCommitted() {
		testClient.get()
			.uri("/committed")
			.header("Host", "www.rewrite-committed.org")
			.exchange()
			.expectStatus()
			.isEqualTo(HttpStatus.FORBIDDEN);
	}

	@Test
	void rewriteLocationResponseHeaderShouldNotThrowWhenResponseCommitted() {
		testClient.get()
			.uri("/committed")
			.header("Host", "www.rewritelocation-committed.org")
			.exchange()
			.expectStatus()
			.isEqualTo(HttpStatus.FORBIDDEN);
	}

	@Test
	void secureHeadersShouldNotThrowWhenResponseCommitted() {
		testClient.get()
			.uri("/committed")
			.header("Host", "www.secureheaders-committed.org")
			.exchange()
			.expectStatus()
			.isEqualTo(HttpStatus.FORBIDDEN);
	}

	/**
	 * A filter that commits the response without calling chain.filter(), simulating
	 * behavior of filters like RequestRateLimiter when denying a request.
	 */
	static class CommitResponseFilter implements GatewayFilter {

		@Override
		public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
			exchange.getResponse().setStatusCode(HttpStatus.FORBIDDEN);
			return exchange.getResponse().setComplete();
		}

	}

	@EnableAutoConfiguration
	@SpringBootConfiguration
	@Import(DefaultTestConfig.class)
	static class TestConfig {

		@Value("${test.uri}")
		String uri;

		@Bean
		public RouteLocator testRouteLocator(RouteLocatorBuilder builder) {
			return builder.routes()
				// SetResponseHeader test
				.route("set_response_header_committed_test", r -> r.path("/committed")
					.and()
					.host("www.set-committed.org")
					.filters(f -> f.filter(new CommitResponseFilter()).setResponseHeader("X-Custom-Header", "value"))
					.uri(uri))
				// RemoveResponseHeader test
				.route("remove_response_header_committed_test",
						r -> r.path("/committed")
							.and()
							.host("www.remove-committed.org")
							.filters(f -> f.filter(new CommitResponseFilter()).removeResponseHeader("X-Custom-Header"))
							.uri(uri))
				// DedupeResponseHeader test
				.route("dedupe_response_header_committed_test",
						r -> r.path("/committed")
							.and()
							.host("www.dedupe-committed.org")
							.filters(f -> f.filter(new CommitResponseFilter())
								.dedupeResponseHeader("X-Custom-Header", "RETAIN_FIRST"))
							.uri(uri))
				// RewriteResponseHeader test
				.route("rewrite_response_header_committed_test",
						r -> r.path("/committed")
							.and()
							.host("www.rewrite-committed.org")
							.filters(f -> f.filter(new CommitResponseFilter())
								.rewriteResponseHeader("X-Custom-Header", "pattern", "replacement"))
							.uri(uri))
				// RewriteLocationResponseHeader test
				.route("rewrite_location_response_header_committed_test",
						r -> r.path("/committed")
							.and()
							.host("www.rewritelocation-committed.org")
							.filters(f -> f.filter(new CommitResponseFilter())
								.rewriteLocationResponseHeader("AS_IN_REQUEST", "Location", null, null))
							.uri(uri))
				// SecureHeaders test
				.route("secure_headers_committed_test",
						r -> r.path("/committed")
							.and()
							.host("www.secureheaders-committed.org")
							.filters(f -> f.filter(new CommitResponseFilter()).secureHeaders())
							.uri(uri))
				.build();
		}

	}

}
