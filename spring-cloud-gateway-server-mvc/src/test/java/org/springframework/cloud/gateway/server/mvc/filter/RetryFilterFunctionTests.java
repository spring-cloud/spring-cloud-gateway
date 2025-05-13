/*
 * Copyright 2013-2025 the original author or authors.
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

package org.springframework.cloud.gateway.server.mvc.filter;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.cloud.gateway.server.mvc.test.HttpbinTestcontainers;
import org.springframework.cloud.gateway.server.mvc.test.LocalServerPortUriResolver;
import org.springframework.cloud.gateway.server.mvc.test.TestLoadBalancerConfig;
import org.springframework.cloud.gateway.server.mvc.test.client.TestRestClient;
import org.springframework.cloud.loadbalancer.annotation.LoadBalancerClient;
import org.springframework.context.annotation.Bean;
import org.springframework.core.log.LogMessage;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerResponse;

import static org.springframework.cloud.gateway.server.mvc.filter.FilterFunctions.adaptCachedBody;
import static org.springframework.cloud.gateway.server.mvc.filter.FilterFunctions.prefixPath;
import static org.springframework.cloud.gateway.server.mvc.filter.RetryFilterFunctions.retry;
import static org.springframework.cloud.gateway.server.mvc.handler.GatewayRouterFunctions.route;
import static org.springframework.cloud.gateway.server.mvc.handler.HandlerFunctions.http;

@SpringBootTest(properties = { "spring.cloud.gateway.function.enabled=false" },
		webEnvironment = WebEnvironment.RANDOM_PORT)
@ContextConfiguration(initializers = HttpbinTestcontainers.class)
public class RetryFilterFunctionTests {

	@LocalServerPort
	int port;

	@Autowired
	TestRestClient restClient;

	@Test
	public void retryWorks() {
		restClient.get().uri("/retry?key=get").exchange().expectStatus().isOk().expectBody(String.class).isEqualTo("3");
		// test for: java.lang.IllegalArgumentException: You have already selected another
		// retry policy
		restClient.get()
			.uri("/retry?key=get2")
			.exchange()
			.expectStatus()
			.isOk()
			.expectBody(String.class)
			.isEqualTo("3");
	}

	@Test
	public void retryBodyWorks() {
		restClient.post()
			.uri("/retrybody?key=post")
			.bodyValue("thebody")
			.exchange()
			.expectStatus()
			.isOk()
			.expectBody(String.class)
			.isEqualTo("3");
	}

	@SpringBootConfiguration
	@EnableAutoConfiguration
	@LoadBalancerClient(name = "httpbin", configuration = TestLoadBalancerConfig.Httpbin.class)
	protected static class TestConfiguration {

		@Bean
		public RouterFunction<ServerResponse> gatewayRouterFunctionsRetry() {
			// @formatter:off
			return route("testretry")
					.GET("/retry", http())
					.before(new LocalServerPortUriResolver())
					.filter(retry(3))
					.filter(prefixPath("/do"))
					.build();
			// @formatter:on
		}

		@Bean
		public RouterFunction<ServerResponse> gatewayRouterFunctionsRetryBody() {
			// @formatter:off
			return route("testretrybody")
					.POST("/retrybody", http())
					.before(new LocalServerPortUriResolver())
					.filter(retry(config -> config.setRetries(3).setSeries(Set.of(HttpStatus.Series.SERVER_ERROR))
							.setMethods(Set.of(HttpMethod.GET, HttpMethod.POST)).setCacheBody(true)))
					.filter(adaptCachedBody())
					.filter(prefixPath("/do"))
					.build();
			// @formatter:on
		}

		@RestController
		protected static class RetryController {

			Log log = LogFactory.getLog(getClass());

			ConcurrentHashMap<String, AtomicInteger> map = new ConcurrentHashMap<>();

			@GetMapping("/do/retry")
			public ResponseEntity<String> retry(@RequestParam("key") String key,
					@RequestParam(name = "count", defaultValue = "3") int count,
					@RequestParam(name = "failStatus", required = false) Integer failStatus) {
				AtomicInteger num = getCount(key);
				int i = num.incrementAndGet();
				log.warn("Retry count: " + i);
				String body = String.valueOf(i);
				if (i < count) {
					HttpStatus httpStatus = HttpStatus.INTERNAL_SERVER_ERROR;
					if (failStatus != null) {
						httpStatus = HttpStatus.resolve(failStatus);
					}
					return ResponseEntity.status(httpStatus).header("X-Retry-Count", body).body("temporarily broken");
				}
				return ResponseEntity.status(HttpStatus.OK).header("X-Retry-Count", body).body(body);
			}

			@PostMapping("/do/retrybody")
			public ResponseEntity<String> retryBody(@RequestParam("key") String key,
					@RequestParam(name = "count", defaultValue = "3") int count, @RequestBody String requestBody) {
				AtomicInteger num = getCount(key);
				int i = num.incrementAndGet();
				log.warn(LogMessage.format("Retry count: %s, body: %s", i, requestBody));
				String body = String.valueOf(i);
				if (!StringUtils.hasText(requestBody)) {
					return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
						.header("X-Retry-Count", body)
						.body("missing body");
				}
				if (i < count) {
					return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
						.header("X-Retry-Count", body)
						.body("temporarily broken");
				}
				return ResponseEntity.status(HttpStatus.OK).header("X-Retry-Count", body).body(body);
			}

			AtomicInteger getCount(String key) {
				return map.computeIfAbsent(key, s -> new AtomicInteger());
			}

		}

	}

}
