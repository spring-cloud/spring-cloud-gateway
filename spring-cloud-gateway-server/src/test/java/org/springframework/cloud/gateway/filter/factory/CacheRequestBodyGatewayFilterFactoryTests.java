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

import java.util.Map;

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
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.cloud.gateway.test.BaseWebClientTests;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.buffer.PooledDataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

@SpringBootTest(webEnvironment = RANDOM_PORT)
@DirtiesContext
public class CacheRequestBodyGatewayFilterFactoryTests extends BaseWebClientTests {

	private static final String BODY_VALUE = "here is request body";

	private static final String BODY_EMPTY = "";

	private static final String BODY_CACHED_EXISTS = "BODY_CACHED_EXISTS";

	@Test
	public void cacheRequestBodyWorks() {
		testClient.post().uri("/post").header("Host", "www.cacherequestbody.org").bodyValue(BODY_VALUE).exchange()
				.expectStatus().isOk().expectBody(Map.class).consumeWith(result -> {
					Map<?, ?> response = result.getResponseBody();
					assertThat(response).isNotNull();

					String responseBody = (String) response.get("data");
					assertThat(responseBody).isEqualTo(BODY_VALUE);
				});
	}

	@Test
	public void cacheRequestBodyEmpty() {
		testClient.post().uri("/post").header("Host", "www.cacherequestbodyempty.org").exchange().expectStatus().isOk()
				.expectBody(Map.class).consumeWith(result -> {
					Map<?, ?> response = result.getResponseBody();
					assertThat(response).isNotNull();

					assertThat(response.get("data")).isNull();
				});
	}

	@Test
	public void cacheRequestBodyExists() {
		testClient.post().uri("/post").header("Host", "www.cacherequestbodyexists.org").exchange().expectStatus()
				.isOk();
	}

	@Test
	public void toStringFormat() {
		CacheRequestBodyGatewayFilterFactory.Config config = new CacheRequestBodyGatewayFilterFactory.Config();
		config.setBodyClass(String.class);
		GatewayFilter filter = new CacheRequestBodyGatewayFilterFactory().apply(config);
		assertThat(filter.toString()).contains("String");
	}

	@EnableAutoConfiguration
	@SpringBootConfiguration
	@Import(DefaultTestConfig.class)
	public static class TestConfig {

		@Value("${test.uri}")
		String uri;

		@Bean
		public RouteLocator testRouteLocator(RouteLocatorBuilder builder) {
			return builder.routes()
					.route("cache_request_body_java_test",
							r -> r.path("/post").and().host("**.cacherequestbody.org")
									.filters(f -> f.prefixPath("/httpbin").cacheRequestBody(String.class)
											.filter(new AssertCachedRequestBodyGatewayFilter(BODY_VALUE))
											.filter(new CheckCachedRequestBodyReleasedGatewayFilter()))
									.uri(uri))
					.route("cache_request_body_empty_java_test",
							r -> r.path("/post").and().host("**.cacherequestbodyempty.org")
									.filters(f -> f.prefixPath("/httpbin").cacheRequestBody(String.class)
											.filter(new AssertCachedRequestBodyGatewayFilter(BODY_EMPTY)))
									.uri(uri))
					.route("cache_request_body_exists_java_test",
							r -> r.path("/post").and().host("**.cacherequestbodyexists.org")
									.filters(f -> f.prefixPath("/httpbin")
											.filter(new SetExchangeCachedRequestBodyGatewayFilter(BODY_CACHED_EXISTS))
											.cacheRequestBody(String.class)
											.filter(new AssertCachedRequestBodyGatewayFilter(BODY_CACHED_EXISTS)))
									.uri(uri))
					.build();
		}

	}

	private static class AssertCachedRequestBodyGatewayFilter implements GatewayFilter {

		private boolean exceptNullBody;

		private String bodyExcepted;

		AssertCachedRequestBodyGatewayFilter(String body) {
			this.exceptNullBody = !StringUtils.hasText(body);
			this.bodyExcepted = body;
		}

		@Override
		public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
			String body = exchange.getAttribute(ServerWebExchangeUtils.CACHED_REQUEST_BODY_ATTR);
			if (exceptNullBody) {
				assertThat(body).isNull();
			}
			else {
				assertThat(body).isEqualTo(bodyExcepted);
			}
			return chain.filter(exchange);
		}

	}

	private static class SetExchangeCachedRequestBodyGatewayFilter implements GatewayFilter {

		private String bodyToSetCache;

		SetExchangeCachedRequestBodyGatewayFilter(String toSet) {
			this.bodyToSetCache = toSet;
		}

		@Override
		public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
			exchange.getAttributes().put(ServerWebExchangeUtils.CACHED_REQUEST_BODY_ATTR, bodyToSetCache);
			return chain.filter(exchange);
		}

	}

	private static class CheckCachedRequestBodyReleasedGatewayFilter implements GatewayFilter {

		CheckCachedRequestBodyReleasedGatewayFilter() {
		}

		@Override
		public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
			return chain.filter(exchange).doAfterTerminate(() -> {
				Object o = exchange.getAttributes()
						.get(CacheRequestBodyGatewayFilterFactory.CACHED_ORIGINAL_REQUEST_BODY_BACKUP_ATTR);
				if (o instanceof PooledDataBuffer dataBuffer) {
					if (dataBuffer.isAllocated()) {
						exchange.getResponse().setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR);
						fail("DataBuffer is not released");
					}
				}
			});
		}

	}

}
