/*
 * Copyright 2013-2019 the original author or authors.
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
 */

package org.springframework.cloud.gateway.filter.factory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import com.netflix.loadbalancer.Server;
import com.netflix.loadbalancer.ServerList;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.junit.Test;
import org.junit.runner.RunWith;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.cloud.gateway.test.BaseWebClientTests;
import org.springframework.cloud.netflix.ribbon.RibbonClient;
import org.springframework.cloud.netflix.ribbon.StaticServerList;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = RANDOM_PORT)
@DirtiesContext
public class RetryGatewayFilterFactoryIntegrationTests extends BaseWebClientTests {

	static ConcurrentHashMap<String, AtomicInteger> retries = new ConcurrentHashMap<>();

	@Test
	public void retryFilterGet() {
		testClient.get().uri("/retry?key=get").exchange().expectStatus().isOk()
				.expectBody(String.class).isEqualTo("3");
	}

	@Test
	public void retryFilterFailure() {
		testClient.get().uri("/retryalwaysfail?key=getjavafailure&count=4")
				.header(HttpHeaders.HOST, "www.retryjava.org").exchange().expectStatus()
				.is5xxServerError().expectBody(String.class).consumeWith(result -> {
					assertThat(result.getResponseBody()).contains("permanently broken");
				});
	}

	@Test
	public void retryFilterGetJavaDsl() {
		testClient.get().uri("/retry?key=getjava&count=2")
				.header(HttpHeaders.HOST, "www.retryjava.org").exchange().expectStatus()
				.isOk().expectBody(String.class).isEqualTo("2");
	}

	@Test
	// TODO: support post
	public void retryFilterPost() {
		testClient.post().uri("/retry?key=post").exchange().expectStatus()
				.is5xxServerError();
		// .expectBody(String.class).isEqualTo("3");
	}

	@Test
	public void retriesSleepyRequest() throws Exception {
		testClient.get()
				.uri("/sleep?key=sleepyRequest&millis=550")
				.header(HttpHeaders.HOST, "www.retryjava.org")
				.exchange()
				.expectStatus()
				.isEqualTo(HttpStatus.GATEWAY_TIMEOUT);

		assertThat(retries.get("sleepyRequest"))
				.isNotNull()
				.hasValue(3);
	}

	@Test
	@SuppressWarnings("unchecked")
	public void retryFilterLoadBalancedWithMultipleServers() {
		String host = "www.retrywithloadbalancer.org";
		testClient.get().uri("/get").header(HttpHeaders.HOST, host).exchange()
				.expectStatus().isOk().expectBody(Map.class).consumeWith(res -> {
					Map body = res.getResponseBody();
					assertThat(body).isNotNull();
					Map<String, Object> headers = (Map<String, Object>) body
							.get("headers");
					assertThat(headers).containsEntry("X-Forwarded-Host", host);
				});
	}

	@RestController
	@EnableAutoConfiguration
	@SpringBootConfiguration
	@Import(DefaultTestConfig.class)
	@RibbonClient(name = "badservice2", configuration = TestBadRibbonConfig.class)
	public static class TestConfig {

		Log log = LogFactory.getLog(getClass());

		@Value("${test.uri}")
		private String uri;

		@RequestMapping("/httpbin/sleep")
		public ResponseEntity<String> sleep(@RequestParam("key") String key,
											@RequestParam("millis") long millisToSleep) {
			int retryCount = getAndIncrementRetryCount(key);
			log.warn("Retry count: " + retryCount);
			try {
				Thread.sleep(millisToSleep);
			} catch (InterruptedException e) {
			}
			return ResponseEntity.status(HttpStatus.OK)
					.header("X-Retry-Count", String.valueOf(retryCount))
					.body("slept " + millisToSleep + " ms");
		}

		@RequestMapping("/httpbin/retryalwaysfail")
		public ResponseEntity<String> retryalwaysfail(@RequestParam("key") String key, @RequestParam(name = "count", defaultValue = "3") int count) {
			int retryCount = getAndIncrementRetryCount(key);
			log.warn("Retry count: " + retryCount);
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
					.header("X-Retry-Count", String.valueOf(retryCount))
					.body("permanently broken");
		}

		@RequestMapping("/httpbin/retry")
		public ResponseEntity<String> retry(@RequestParam("key") String key, @RequestParam(name = "count", defaultValue = "3") int count) {
			int retryCount = getAndIncrementRetryCount(key);
			log.warn("Retry count: " + retryCount);
			String body = String.valueOf(retryCount);
			if (retryCount < count) {
				return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
						.header("X-Retry-Count", body)
						.body("temporarily broken");
			}
			return ResponseEntity.status(HttpStatus.OK)
					.header("X-Retry-Count", body)
					.body(body);
		}

		private int getAndIncrementRetryCount(@RequestParam("key") String key) {
			AtomicInteger num = retries.computeIfAbsent(key, s -> new AtomicInteger());
			return num.incrementAndGet();
		}

		@Bean
		public RouteLocator hystrixRouteLocator(RouteLocatorBuilder builder) {
			return builder.routes()
					.route("retry_java", r -> r.host("**.retryjava.org")
							.filters(f -> f.prefixPath("/httpbin")
									.retry(config -> config.setRetries(2)))
							.uri(uri))
					.route("retry_with_loadbalancer",
							r -> r.host("**.retrywithloadbalancer.org")
									.filters(f -> f.prefixPath("/httpbin")
											.retry(config -> config.setRetries(2)))
									.uri("lb://badservice2"))
					.build();
		}

	}

	protected static class TestBadRibbonConfig {

		@LocalServerPort
		protected int port = 0;

		@Bean
		public ServerList<Server> ribbonServerList() {
			return new StaticServerList<>(
					new Server("https", "localhost.domain.doesnot.exist", this.port),
					new Server("localhost", this.port));
		}

	}

}
