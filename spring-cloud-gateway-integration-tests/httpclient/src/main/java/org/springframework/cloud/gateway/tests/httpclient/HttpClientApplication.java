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

package org.springframework.cloud.gateway.tests.httpclient;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.core5.util.Timeout;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.cloud.client.DefaultServiceInstance;
import org.springframework.cloud.loadbalancer.annotation.LoadBalancerClient;
import org.springframework.cloud.loadbalancer.core.ServiceInstanceListSupplier;
import org.springframework.cloud.loadbalancer.support.ServiceInstanceListSuppliers;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerResponse;

import static org.springframework.cloud.gateway.server.mvc.filter.FilterFunctions.prefixPath;
import static org.springframework.cloud.gateway.server.mvc.filter.FrameworkRetryFilterFunctions.frameworkRetry;
import static org.springframework.cloud.gateway.server.mvc.filter.GatewayRetryFilterFunctions.retry;
import static org.springframework.cloud.gateway.server.mvc.filter.LoadBalancerFilterFunctions.lb;
import static org.springframework.cloud.gateway.server.mvc.handler.GatewayRouterFunctions.route;
import static org.springframework.cloud.gateway.server.mvc.handler.HandlerFunctions.http;

/**
 * @author jiangyuan
 */
@SpringBootConfiguration
@EnableAutoConfiguration
@LoadBalancerClient(name = "myservice", configuration = MyServiceConf.class)
public class HttpClientApplication {

	public static void main(String[] args) {
		SpringApplication.run(HttpClientApplication.class, args);
	}

	@Bean
	public HttpComponentsClientHttpRequestFactory httpComponentsClientHttpRequestFactory() {
		PoolingHttpClientConnectionManager connectionManager = new PoolingHttpClientConnectionManager();
		connectionManager.setMaxTotal(2);
		connectionManager.setDefaultMaxPerRoute(2);

		CloseableHttpClient httpClient = HttpClients.custom()
			.setConnectionManager(connectionManager)
			.setDefaultRequestConfig(
					RequestConfig.custom().setConnectionRequestTimeout(Timeout.of(Duration.ofMillis(3000))).build())
			.build();

		HttpComponentsClientHttpRequestFactory factory = new HttpComponentsClientHttpRequestFactory(httpClient);
		return factory;
	}

	@Bean
	public RouterFunction<ServerResponse> gatewayRouterFunctionsRetry() {
		return route("test-retry").GET("/retry", http())
			.filter(lb("myservice"))
			.filter(prefixPath("/do"))
			.filter(retry(3))
			.build();
	}

	@Bean
	public RouterFunction<ServerResponse> gatewayRouterFunctionsFrameworkRetry() {
		return route("test-retry").GET("/frameworkretry", http())
			.filter(lb("myservice"))
			.filter(prefixPath("/do"))
			.filter(frameworkRetry(3))
			.build();
	}

	@RestController
	protected static class RetryController {

		Log log = LogFactory.getLog(getClass());

		ConcurrentHashMap<String, AtomicInteger> map = new ConcurrentHashMap<>();

		@GetMapping("/do/frameworkretry")
		public ResponseEntity<String> frameworkRetry(@RequestParam("key") String key,
				@RequestParam(name = "count", defaultValue = "3") int count,
				@RequestParam(name = "failStatus", required = false) Integer failStatus) {
			return retry(key, count, failStatus);
		}

		@GetMapping("/do/retry")
		public ResponseEntity<String> retry(@RequestParam("key") String key,
				@RequestParam(name = "count", defaultValue = "3") int count,
				@RequestParam(name = "failStatus", required = false) Integer failStatus) {
			AtomicInteger num = map.computeIfAbsent(key, s -> new AtomicInteger());
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
			map = new ConcurrentHashMap<>();
			return ResponseEntity.status(HttpStatus.OK).header("X-Retry-Count", body).body(body);
		}

	}

}

class MyServiceConf {

	@Value("${local.server.port}")
	private int port = 0;

	@Bean
	public ServiceInstanceListSupplier staticServiceInstanceListSupplier() {
		return ServiceInstanceListSuppliers.from("myservice",
				new DefaultServiceInstance("myservice-1", "myservice", "localhost", port, false));
	}

}
