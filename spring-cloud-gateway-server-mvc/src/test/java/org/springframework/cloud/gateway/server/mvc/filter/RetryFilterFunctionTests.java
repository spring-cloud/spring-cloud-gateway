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

import java.time.Duration;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hc.core5.util.Timeout;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.cloud.gateway.server.mvc.config.GatewayMvcProperties;
import org.springframework.cloud.gateway.server.mvc.handler.ProxyExchange;
import org.springframework.cloud.gateway.server.mvc.handler.ProxyExchangeHandlerFunction;
import org.springframework.cloud.gateway.server.mvc.handler.RestClientProxyExchange;
import org.springframework.cloud.gateway.server.mvc.test.HttpbinTestcontainers;
import org.springframework.cloud.gateway.server.mvc.test.LocalServerPortUriResolver;
import org.springframework.cloud.gateway.server.mvc.test.TestLoadBalancerConfig;
import org.springframework.cloud.gateway.server.mvc.test.client.TestRestClient;
import org.springframework.cloud.loadbalancer.annotation.LoadBalancerClient;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.core.log.LogMessage;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;
import org.springframework.web.servlet.function.HandlerFunction;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerResponse;

import static org.springframework.cloud.gateway.server.mvc.filter.FilterFunctions.adaptCachedBody;
import static org.springframework.cloud.gateway.server.mvc.filter.FilterFunctions.prefixPath;
import static org.springframework.cloud.gateway.server.mvc.filter.FilterFunctions.setPath;
import static org.springframework.cloud.gateway.server.mvc.filter.RetryFilterFunctions.retry;
import static org.springframework.cloud.gateway.server.mvc.handler.GatewayRouterFunctions.route;
import static org.springframework.cloud.gateway.server.mvc.handler.HandlerFunctions.http;

@SuppressWarnings("unchecked")
@SpringBootTest(properties = {}, webEnvironment = WebEnvironment.RANDOM_PORT)
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

	@Test
	public void retryWorksWithHttpComponentsClient() {
		restClient.get()
			.uri("/retrywithhttpcomponentsclient?key=retryWorksWithHttpComponentsClient")
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
		public RouterFunction<ServerResponse> gatewayRouterFunctionsRetryWithHttpComponentsClient(
				GatewayMvcProperties properties,
				ObjectProvider<HttpHeadersFilter.RequestHttpHeadersFilter> requestHttpHeadersFilters,
				ObjectProvider<HttpHeadersFilter.ResponseHttpHeadersFilter> responseHttpHeadersFilters,
				ApplicationContext applicationContext) {

			// build httpComponents client factory
			ClientHttpRequestFactory clientHttpRequestFactory = ClientHttpRequestFactoryBuilder.httpComponents()
				.withConnectionManagerCustomizer(builder -> builder.setMaxConnTotal(2).setMaxConnPerRoute(2))
				.withDefaultRequestConfigCustomizer(
						c -> c.setConnectionRequestTimeout(Timeout.of(Duration.ofMillis(3000))))
				.build();

			// build proxyExchange use httpComponents
			RestClient.Builder restClientBuilder = RestClient.builder();
			restClientBuilder.requestFactory(clientHttpRequestFactory);
			ProxyExchange proxyExchange = new RestClientProxyExchange(restClientBuilder.build(), properties);

			// build handler function use httpComponents
			ProxyExchangeHandlerFunction function = new ProxyExchangeHandlerFunction(proxyExchange,
					requestHttpHeadersFilters, responseHttpHeadersFilters);
			function.onApplicationEvent(new ContextRefreshedEvent(applicationContext));

			// @formatter:off
			return route("testretrywithhttpcomponentsclient")
					.GET("/retrywithhttpcomponentsclient", function)
					.before(new LocalServerPortUriResolver())
					.filter(retry(3))
					.filter(setPath("/retry"))
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
