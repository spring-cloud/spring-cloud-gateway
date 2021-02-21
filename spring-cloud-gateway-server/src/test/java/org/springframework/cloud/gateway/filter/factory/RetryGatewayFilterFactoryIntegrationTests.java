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

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.hamcrest.CoreMatchers;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import reactor.core.publisher.Mono;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.OutputCaptureRule;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.cloud.client.DefaultServiceInstance;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.RetryGatewayFilterFactory.RetryConfig;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.cloud.gateway.test.BaseWebClientTests;
import org.springframework.cloud.loadbalancer.annotation.LoadBalancerClient;
import org.springframework.cloud.loadbalancer.core.ServiceInstanceListSupplier;
import org.springframework.cloud.loadbalancer.support.ServiceInstanceListSuppliers;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = RANDOM_PORT,
		properties = { "spring.cloud.gateway.httpclient.connect-timeout=500",
				"spring.cloud.gateway.httpclient.response-timeout=2s",
				"logging.level.org.springframework.cloud.gateway.filter.factory.RetryGatewayFilterFactory=TRACE" })
@DirtiesContext
// default filter AddResponseHeader suppresses bug
// https://github.com/spring-cloud/spring-cloud-gateway/issues/1315,
// so we use only PrefixPath filter
@ActiveProfiles("retrytests")
public class RetryGatewayFilterFactoryIntegrationTests extends BaseWebClientTests {

	@Rule
	public final OutputCaptureRule capture = new OutputCaptureRule();

	@Test
	public void retryFilterGet() {
		testClient.get().uri("/retry?key=get").exchange().expectStatus().isOk().expectBody(String.class).isEqualTo("3");
	}

	@Test
	public void retryFilterFailure() {
		testClient.mutate().responseTimeout(Duration.ofSeconds(10)).build().get()
				.uri("/retryalwaysfail?key=getjavafailure&count=4").header(HttpHeaders.HOST, "www.retryjava.org")
				.exchange().expectStatus().is5xxServerError().expectBody(String.class).consumeWith(result -> {
					assertThat(result.getResponseBody()).contains("permanently broken");
				});
	}

	@Test
	public void retryWithBackoff() {
		// @formatter:off
		testClient.get()
				.uri("/retry?key=retry-with-backoff&count=3")
				.header(HttpHeaders.HOST, "www.retrywithbackoff.org")
				.exchange()
				.expectStatus().isOk()
				.expectHeader().value("X-Retry-Count", CoreMatchers.equalTo("3"));
		// @formatter:on
	}

	@Test
	public void retryFilterGetJavaDsl() {
		testClient.get().uri("/retry?key=getjava&count=2").header(HttpHeaders.HOST, "www.retryjava.org").exchange()
				.expectStatus().isOk().expectBody(String.class).isEqualTo("2");
	}

	@Test
	public void retryFilterPost() {
		testClient.post().uri("/retrypost?key=postconfig&expectedbody=HelloConfig")
				.header(HttpHeaders.HOST, "www.retrypostconfig.org").bodyValue("HelloConfig").exchange().expectStatus()
				.isOk().expectBody(String.class).isEqualTo("3");
		assertThat(this.capture.toString()).contains("disposing response connection before next iteration");
	}

	@Test
	public void retryFilterPostJavaDsl() {
		testClient.post().uri("/retrypost?key=post&expectedbody=Hello").header(HttpHeaders.HOST, "www.retryjava.org")
				.bodyValue("Hello").exchange().expectStatus().isOk().expectBody(String.class).isEqualTo("3");
	}

	@Test
	public void retryFilterPostOneTime() {
		testClient.post().uri("/retrypost?key=retryFilterPostOneTime&expectedbody=HelloGateway&count=1")
				.header(HttpHeaders.HOST, "www.retrypostonceconfig.org").bodyValue("HelloGateway").exchange()
				.expectStatus().isOk();
		assertThat(this.capture.toString()).contains("setting new iteration in attr 0");
		assertThat(this.capture.toString()).doesNotContain("setting new iteration in attr 1");
	}

	@Test
	public void retriesSleepyRequest() throws Exception {
		testClient.mutate().responseTimeout(Duration.ofSeconds(10)).build().get()
				.uri("/sleep?key=sleepyRequest&millis=3000").header(HttpHeaders.HOST, "www.retryjava.org").exchange()
				.expectStatus().isEqualTo(HttpStatus.GATEWAY_TIMEOUT);

		assertThat(TestConfig.map.get("sleepyRequest")).isNotNull().hasValue(3);
	}

	@Test
	public void shouldNotRetryWhenSleepyRequestPost() throws Exception {
		testClient.mutate().responseTimeout(Duration.ofSeconds(10)).build().post()
				.uri("/sleep?key=notRetriesSleepyRequestPost&millis=3000")
				.header(HttpHeaders.HOST, "www.retry-only-get.org").exchange().expectStatus()
				.isEqualTo(HttpStatus.GATEWAY_TIMEOUT);

		assertThat(TestConfig.map.get("notRetriesSleepyRequestPost")).isNotNull().hasValue(1);
	}

	@Test
	public void shouldNotRetryWhenSleepyRequestPostWithBody() throws Exception {
		testClient.mutate().responseTimeout(Duration.ofSeconds(10)).build().post()
				.uri("/sleep?key=notRetriesSleepyRequestPostWithBody&millis=3000")
				.header(HttpHeaders.HOST, "www.retry-only-get.org").bodyValue("retry sleepy post with body").exchange()
				.expectStatus().isEqualTo(HttpStatus.GATEWAY_TIMEOUT);

		assertThat(TestConfig.map.get("notRetriesSleepyRequestPostWithBody")).isNotNull().hasValue(1);
	}

	@Test
	public void shouldRetryWhenSleepyRequestGet() throws Exception {
		testClient.mutate().responseTimeout(Duration.ofSeconds(10)).build().get()
				.uri("/sleep?key=sleepyRequestGet&millis=3000").header(HttpHeaders.HOST, "www.retry-only-get.org")
				.exchange().expectStatus().isEqualTo(HttpStatus.GATEWAY_TIMEOUT);

		assertThat(TestConfig.map.get("sleepyRequestGet")).isNotNull().hasValue(3);
	}

	@Test
	@SuppressWarnings("unchecked")
	public void retryFilterLoadBalancedWithMultipleServers() {
		String host = "www.retrywithloadbalancer.org";
		testClient.get().uri("/get").header(HttpHeaders.HOST, host).exchange().expectStatus().isOk()
				.expectBody(Map.class).consumeWith(res -> {
					Map body = res.getResponseBody();
					assertThat(body).isNotNull();
					Map<String, Object> headers = (Map<String, Object>) body.get("headers");
					assertThat(headers).containsEntry("X-Forwarded-Host", host);
				});
	}

	@Test
	public void toStringFormat() {
		RetryConfig config = new RetryConfig();
		config.setRetries(4);
		config.setMethods(HttpMethod.GET);
		config.setSeries(HttpStatus.Series.SERVER_ERROR);
		config.setExceptions(IOException.class);
		GatewayFilter filter = new RetryGatewayFilterFactory().apply(config);
		assertThat(filter.toString()).contains("4").contains("[GET]").contains("[SERVER_ERROR]")
				.contains("[IOException]");
	}

	@RestController
	@EnableAutoConfiguration
	@SpringBootConfiguration
	@Import(DefaultTestConfig.class)
	@LoadBalancerClient(name = "badservice2", configuration = TestBadLoadBalancerConfig.class)
	public static class TestConfig {

		Log log = LogFactory.getLog(getClass());

		static ConcurrentHashMap<String, AtomicInteger> map = new ConcurrentHashMap<>();

		@Value("${test.uri}")
		private String uri;

		@RequestMapping("/httpbin/sleep")
		public Mono<ResponseEntity<String>> sleep(@RequestParam("key") String key,
				@RequestParam("millis") long millisToSleep) {
			AtomicInteger num = getCount(key);
			int retryCount = num.incrementAndGet();
			log.warn("Retry count: " + retryCount);
			return Mono.delay(Duration.ofMillis(millisToSleep)).thenReturn(ResponseEntity.status(HttpStatus.OK)
					.header("X-Retry-Count", String.valueOf(retryCount)).body("slept " + millisToSleep + " ms"));
		}

		@RequestMapping("/httpbin/retryalwaysfail")
		public ResponseEntity<String> retryalwaysfail(@RequestParam("key") String key,
				@RequestParam(name = "count", defaultValue = "3") int count) {
			AtomicInteger num = getCount(key);
			int i = num.incrementAndGet();
			log.warn("Retry count: " + i);
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).header("X-Retry-Count", String.valueOf(i))
					.body("permanently broken");
		}

		@RequestMapping("/httpbin/retrypost")
		public ResponseEntity<String> retrypost(@RequestParam("key") String key,
				@RequestParam(name = "count", defaultValue = "3") int count,
				@RequestParam("expectedbody") String expectedbody, @RequestBody String body) {
			ResponseEntity<String> response = retry(key, count);
			if (!expectedbody.equals(body)) {
				AtomicInteger num = getCount(key);
				return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
						.header("X-Retry-Count", String.valueOf(num)).body("body did not match on try" + num);
			}
			return response;
		}

		@RequestMapping("/httpbin/retry")
		public ResponseEntity<String> retry(@RequestParam("key") String key,
				@RequestParam(name = "count", defaultValue = "3") int count) {
			AtomicInteger num = getCount(key);
			int i = num.incrementAndGet();
			log.warn("Retry count: " + i);
			String body = String.valueOf(i);
			if (i < count) {
				return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).header("X-Retry-Count", body)
						.body("temporarily broken");
			}
			return ResponseEntity.status(HttpStatus.OK).header("X-Retry-Count", body).body(body);
		}

		AtomicInteger getCount(String key) {
			return map.computeIfAbsent(key, s -> new AtomicInteger());
		}

		@Bean
		public RouteLocator hystrixRouteLocator(RouteLocatorBuilder builder) {
			return builder.routes().route("retry_java",
					r -> r.host("**.retryjava.org")
							.filters(f -> f.prefixPath("/httpbin")
									.retry(config -> config.setRetries(2).setMethods(HttpMethod.POST, HttpMethod.GET)))
							.uri(uri))
					.route("retry_only_get",
							r -> r.host("**.retry-only-get.org")
									.filters(f -> f.prefixPath("/httpbin")
											.retry(config -> config.setRetries(2).setMethods(HttpMethod.GET)))
									.uri(uri))
					.route("retry_with_backoff", r -> r.host("**.retrywithbackoff.org")
							.filters(f -> f.prefixPath("/httpbin").retry(config -> {
								config.setRetries(2).setBackoff(Duration.ofMillis(100), null, 2, true);
							})).uri(uri))

					.route("retry_with_loadbalancer",
							r -> r.host("**.retrywithloadbalancer.org")
									.filters(f -> f.prefixPath("/httpbin").retry(config -> config.setRetries(2)))
									.uri("lb://badservice2"))
					.build();
		}

	}

	protected static class TestBadLoadBalancerConfig {

		@LocalServerPort
		protected int port = 0;

		@Bean
		public ServiceInstanceListSupplier staticServiceInstanceListSupplier() {
			return ServiceInstanceListSuppliers.from("badservice2",
					new DefaultServiceInstance("doesnotexist1", "badservice2", "localhost.domain.doesnot.exist", port,
							true),
					new DefaultServiceInstance("badservice2-1", "badservice2", "localhost", port, false));
		}

	}

}
