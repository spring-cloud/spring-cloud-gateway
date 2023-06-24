/*
 * Copyright 2013-2023 the original author or authors.
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

package org.springframework.cloud.gateway.server.mvc;

import java.net.URI;
import java.time.Duration;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.caffeine.CaffeineProxyManager;
import io.github.bucket4j.distributed.proxy.AsyncProxyManager;
import io.github.bucket4j.distributed.remote.RemoteBucketState;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.cloud.client.DefaultServiceInstance;
import org.springframework.cloud.gateway.server.mvc.test.LocalServerPortUriResolver;
import org.springframework.cloud.gateway.server.mvc.test.client.TestRestClient;
import org.springframework.cloud.loadbalancer.annotation.LoadBalancerClient;
import org.springframework.cloud.loadbalancer.core.ServiceInstanceListSupplier;
import org.springframework.cloud.loadbalancer.support.ServiceInstanceListSuppliers;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.cloud.gateway.server.mvc.filter.Bucket4jFilterFunctions.rateLimit;
import static org.springframework.cloud.gateway.server.mvc.filter.CircuitBreakerFilterFunctions.circuitBreaker;
import static org.springframework.cloud.gateway.server.mvc.filter.FilterFunctions.addRequestHeader;
import static org.springframework.cloud.gateway.server.mvc.filter.FilterFunctions.addRequestParameter;
import static org.springframework.cloud.gateway.server.mvc.filter.FilterFunctions.addResponseHeader;
import static org.springframework.cloud.gateway.server.mvc.filter.FilterFunctions.prefixPath;
import static org.springframework.cloud.gateway.server.mvc.filter.FilterFunctions.setPath;
import static org.springframework.cloud.gateway.server.mvc.filter.FilterFunctions.setStatus;
import static org.springframework.cloud.gateway.server.mvc.filter.FilterFunctions.stripPrefix;
import static org.springframework.cloud.gateway.server.mvc.filter.LoadBalancerFilterFunctions.lb;
import static org.springframework.cloud.gateway.server.mvc.filter.RetryFilterFunctions.retry;
import static org.springframework.cloud.gateway.server.mvc.handler.HandlerFunctions.http;
import static org.springframework.cloud.gateway.server.mvc.predicate.GatewayRequestPredicates.cookie;
import static org.springframework.cloud.gateway.server.mvc.predicate.GatewayRequestPredicates.header;
import static org.springframework.cloud.gateway.server.mvc.predicate.GatewayRequestPredicates.host;
import static org.springframework.web.servlet.function.RequestPredicates.GET;
import static org.springframework.web.servlet.function.RequestPredicates.path;
import static org.springframework.web.servlet.function.RouterFunctions.route;

@SuppressWarnings("unchecked")
@SpringBootTest(properties = {}, webEnvironment = WebEnvironment.RANDOM_PORT)
public class ServerMvcIntegrationTests {

	@LocalServerPort
	int port;

	@Autowired
	TestRestTemplate restTemplate;

	@Autowired
	TestRestClient restClient;

	@Test
	public void nonGatewayRouterFunctionWorks() {
		restClient.get().uri("/hello").exchange().expectStatus().isOk().expectBody(String.class).isEqualTo("Hello");
	}

	@SuppressWarnings("rawtypes")
	@Test
	public void addRequestHeaderWorks() {
		ResponseEntity<Map> response = restTemplate.getForEntity("/get", Map.class);
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		Map<String, Object> map0 = response.getBody();
		assertThat(map0).isNotEmpty().containsKey("headers");
		Map<String, Object> headers0 = (Map<String, Object>) map0.get("headers");
		// TODO: assert headers case insensitive
		assertThat(headers0).containsEntry("x-foo", "Bar");

		restClient.get().uri("/get").exchange().expectStatus().isOk().expectBody(Map.class).consumeWith(res -> {
			Map<String, Object> map = res.getResponseBody();
			assertThat(map).isNotEmpty().containsKey("headers");
			Map<String, Object> headers = (Map<String, Object>) map.get("headers");
			assertThat(headers).containsEntry("x-foo", "Bar");
		});
	}

	@Test
	public void addRequestParameterWorks() {
		restClient.get().uri("/anything/addrequestparam").exchange().expectStatus().isOk().expectBody(Map.class)
				.consumeWith(res -> {
					Map<String, Object> map = res.getResponseBody();
					assertThat(map).isNotEmpty().containsKey("args");
					Map<String, Object> args = (Map<String, Object>) map.get("args");
					assertThat(args).containsEntry("param1", Collections.singletonList("param1val"));
				});
	}

	@Test
	public void removeHopByHopRequestHeadersFilterWorks() {
		restClient.get().uri("/anything/removehopbyhoprequestheaders").exchange().expectStatus().isOk()
				.expectBody(Map.class).consumeWith(res -> {
					Map<String, Object> map = res.getResponseBody();
					assertThat(map).isNotEmpty().containsKey("headers");
					Map<String, Object> headers = (Map<String, Object>) map.get("headers");
					assertThat(headers).doesNotContainKeys("x-application-context");
				});
	}

	@Test
	public void setPathWorks() {
		restClient.get().uri("/mycustompathextra1").exchange().expectStatus().isOk().expectBody(Map.class)
				.consumeWith(res -> {
					Map<String, Object> map = res.getResponseBody();
					assertThat(map).isNotEmpty().containsKey("args");
					Map<String, Object> args = (Map<String, Object>) map.get("args");
					assertThat(args).containsEntry("param1", Collections.singletonList("param1valextra1"));
				});
	}

	@Test
	public void stripPathWorks() {
		restClient.get().uri("/long/path/to/get").exchange().expectStatus().isOk().expectBody(Map.class)
				.consumeWith(res -> {
					Map<String, Object> map = res.getResponseBody();
					assertThat(map).isNotEmpty().containsKey("headers");
					Map<String, Object> headers = (Map<String, Object>) map.get("headers");
					assertThat(headers).containsEntry("x-test", "stripPrefix");
				});
	}

	@Test
	public void setStatusGatewayRouterFunctionWorks() {
		restClient.get().uri("/status/201").exchange().expectStatus().isEqualTo(HttpStatus.TOO_MANY_REQUESTS)
				.expectHeader().valueEquals("x-status", "201").expectBody(String.class).isEqualTo("Failed with 201");
	}

	@Test
	public void addResponseHeaderWorks() {
		restClient.get().uri("/anything/addresheader").exchange().expectStatus().isOk().expectBody(Map.class)
				.consumeWith(res -> {
					Map<String, Object> map = res.getResponseBody();
					assertThat(map).isNotEmpty().containsKey("headers");
					Map<String, Object> headers = (Map<String, Object>) map.get("headers");
					assertThat(headers).doesNotContainKey("x-bar");
					assertThat(res.getResponseHeaders()).containsEntry("x-bar", Collections.singletonList("val1"));
				});
	}

	@Test
	public void postWorks() {
		restClient.post().uri("/post").bodyValue("Post Value").exchange().expectStatus().isOk().expectBody(Map.class)
				.consumeWith(res -> {
					Map<String, Object> map = res.getResponseBody();
					assertThat(map).isNotEmpty().containsEntry("data", "Post Value");
				});
	}

	@Test
	public void loadbalancerWorks() {
		restClient.get().uri("/anything/loadbalancer").exchange().expectStatus().isOk().expectBody(Map.class)
				.consumeWith(res -> {
					Map<String, Object> map = res.getResponseBody();
					assertThat(map).isNotEmpty().containsKey("headers");
					Map<String, Object> headers = (Map<String, Object>) map.get("headers");
					assertThat(headers).containsEntry("x-test", "loadbalancer");
				});
	}

	@Test
	public void hostPredicateWorks() {
		restClient.get().uri("/anything/hostpredicate").header("Host", "www1.myjavadslhost.com").exchange()
				.expectStatus().isOk().expectHeader().valueEquals("X-SubDomain", "www1");
	}

	@Test
	public void circuitBreakerFallbackWorks() {
		restClient.get().uri("/anything/circuitbreakerfallback").exchange().expectStatus().isOk()
				.expectBody(String.class).isEqualTo("Hello");
	}

	@Test
	public void circuitBreakerNoFallbackWorks() {
		restClient.get().uri("/anything/circuitbreakernofallback").exchange().expectStatus()
				.isEqualTo(HttpStatus.GATEWAY_TIMEOUT);
	}

	@Test
	public void retryWorks() {
		restClient.get().uri("/retry?key=get").exchange().expectStatus().isOk().expectBody(String.class).isEqualTo("3");
	}

	@Test
	public void rateLimitWorks() {
		restClient.get().uri("/anything/ratelimit").exchange().expectStatus().isOk();
		restClient.get().uri("/anything/ratelimit").exchange().expectStatus().isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
	}

	@Test
	public void headerRegexWorks() {
		restClient.get().uri("/headerregex").exchange().expectStatus().isNotFound();
		restClient.get().uri("/headerregex").header("X-MyHeader", "foo").exchange().expectStatus().isOk()
				.expectBody(Map.class).consumeWith(res -> {
					Map<String, Object> map = res.getResponseBody();
					assertThat(map).isNotEmpty().containsKey("headers");
					Map<String, Object> headers = (Map<String, Object>) map.get("headers");
					assertThat(headers).containsEntry("x-myheader", "foo");
				});
	}

	@Test
	public void cookieRegexWorks() {
		restClient.get().uri("/cookieregex").exchange().expectStatus().isNotFound();
		restClient.get().uri("/cookieregex").cookie("mycookie", "foo").exchange().expectStatus().isOk()
				.expectBody(Map.class).consumeWith(res -> {
					Map<String, Object> map = res.getResponseBody();
					assertThat(map).isNotEmpty().containsKey("headers");
					Map<String, Object> headers = (Map<String, Object>) map.get("headers");
					assertThat(headers).containsEntry("cookie", "mycookie=foo");
				});
	}

	@SpringBootConfiguration
	@EnableAutoConfiguration
	@LoadBalancerClient(name = "testservice", configuration = TestLoadBalancerConfig.class)
	protected static class TestConfiguration {

		@Bean
		TestHandler testHandler() {
			return new TestHandler();
		}

		@Bean
		RetryController retryController() {
			return new RetryController();
		}

		@Bean
		public AsyncProxyManager<String> caffeineProxyManager() {
			Caffeine<String, RemoteBucketState> builder = (Caffeine) Caffeine.newBuilder().maximumSize(100);
			return new CaffeineProxyManager<>(builder, Duration.ofMinutes(1)).asAsync();
		}

		@Bean
		public RouterFunction<ServerResponse> nonGatewayRouterFunctions(TestHandler testHandler) {
			return route(GET("/hello"), testHandler::hello);
		}

		@Bean
		public RouterFunction<ServerResponse> gatewayRouterFunctionsAddReqHeader() {
			return route(GET("/get"), http()).filter(new LocalServerPortUriResolver())
					.filter(addRequestHeader("X-Foo", "Bar")).filter(prefixPath("/httpbin"));
		}

		@Bean
		public RouterFunction<ServerResponse> gatewayRouterFunctionsSetStatus() {
			// @formatter:off
			return route()
					.GET("/status/{status}", http())
						.filter(new LocalServerPortUriResolver())
						.filter(prefixPath("/httpbin"))
						.filter(setStatus(HttpStatus.TOO_MANY_REQUESTS))
						.filter(addResponseHeader("X-Status", "{status}"))
					// TODO: Filters apply to all routes in a builder
					//.GET("/anything/addresheader", http())
					//	.filter(new LocalServerPortUriResolver())
					//	.filter(prefixPath("/httpbin"))
					//	.filter(addResponseHeader("X-Bar", "val1"))
					.build();
			// @formatter:on
		}

		@Bean
		public RouterFunction<ServerResponse> gatewayRouterFunctionsAddResponseHeader() {
			// @formatter:off
			return route(GET("/anything/addresheader"), http())
					.filter(new LocalServerPortUriResolver())
					.filter(prefixPath("/httpbin"))
					.filter(addResponseHeader("X-Bar", "val1"));
			// @formatter:on
		}

		@Bean
		public RouterFunction<ServerResponse> gatewayRouterFunctionsAddRequestParam() {
			// @formatter:off
			return route(GET("/anything/addrequestparam"), http())
					.filter(new LocalServerPortUriResolver())
					.filter(prefixPath("/httpbin"))
					.filter(addRequestParameter("param1", "param1val"));
			// @formatter:on
		}

		@Bean
		public RouterFunction<ServerResponse> gatewayRouterFunctionsSetPath() {
			// @formatter:off
			return route(GET("/mycustompath{extra}"), http())
					.filter(new LocalServerPortUriResolver())
					.filter(setPath("/httpbin/anything/mycustompath{extra}"))
					.filter(addRequestParameter("param1", "param1val{extra}"));
			// @formatter:on
		}

		@Bean
		public RouterFunction<ServerResponse> gatewayRouterFunctionsStripPath() {
			// @formatter:off
			return route(GET("/long/path/to/get"), http())
					.filter(new LocalServerPortUriResolver())
					.filter(prefixPath("/httpbin"))
					.filter(stripPrefix(3))
					.filter(addRequestHeader("X-Test", "stripPrefix"));
			// @formatter:on
		}

		@Bean
		public RouterFunction<ServerResponse> gatewayRouterFunctionsRemoveHopByHopRequestHeaders() {
			// @formatter:off
			return route(GET("/anything/removehopbyhoprequestheaders"), http())
					.filter(new LocalServerPortUriResolver())
					.filter(prefixPath("/httpbin"))
					.filter(addRequestHeader("x-application-context", "context-id1"));
			// @formatter:on
		}

		@Bean
		public RouterFunction<ServerResponse> gatewayRouterFunctionsPost() {
			// @formatter:off
			return route()
					.POST("/post", http())
					.filter(new LocalServerPortUriResolver())
					.filter(prefixPath("/httpbin"))
					.build();
			// @formatter:on
		}

		@Bean
		public RouterFunction<ServerResponse> gatewayRouterFunctionsLoadBalancer() {
			// @formatter:off
			return route()
					.GET("/anything/loadbalancer", http())
					.filter(lb("testservice"))
					.filter(prefixPath("/httpbin"))
					.filter(addRequestHeader("X-Test", "loadbalancer"))
					.build();
			// @formatter:on
		}

		@Bean
		public RouterFunction<ServerResponse> gatewayRouterFunctionsHost() {
			// @formatter:off
			return route(host("{sub}.myjavadslhost.com").and(path("/anything/hostpredicate")), http())
					.filter(new LocalServerPortUriResolver())
					.filter(prefixPath("/httpbin"))
					.filter(addResponseHeader("X-SubDomain", "{sub}"));
			// @formatter:on
		}

		@Bean
		public RouterFunction<ServerResponse> gatewayRouterFunctionsCircuitBreakerFallback() {
			// @formatter:off
			return route(path("/anything/circuitbreakerfallback"), http(URI.create("https://nonexistantdomain.com1234")))
					.filter(circuitBreaker("mycb1", "/hello"))
					.filter(prefixPath("/httpbin"));
			// @formatter:on
		}

		@Bean
		public RouterFunction<ServerResponse> gatewayRouterFunctionsCircuitBreakerNoFallback() {
			// @formatter:off
			return route(path("/anything/circuitbreakernofallback"), http())
					.filter(new LocalServerPortUriResolver())
					.filter(circuitBreaker("mycb1", null))
					.filter(setPath("/httpbin/delay/5"));
			// @formatter:on
		}

		@Bean
		public RouterFunction<ServerResponse> gatewayRouterFunctionsRetry() {
			// @formatter:off
			return route(path("/retry"), http())
					.filter(new LocalServerPortUriResolver())
					.filter(retry(3))
					.filter(prefixPath("/httpbin"));
			// @formatter:on
		}

		@Bean
		public RouterFunction<ServerResponse> gatewayRouterFunctionsRateLimit() {
			// @formatter:off
			return route(GET("/anything/ratelimit"), http())
					.filter(new LocalServerPortUriResolver())
					//.filter(rateLimit(1, Duration.ofMinutes(1), request -> "ratelimittest1min"))
					.filter(rateLimit(c -> c.setCapacity(1)
							.setPeriod(Duration.ofMinutes(1))
							.setKeyResolver(request -> "ratelimitttest1min")))
					.filter(prefixPath("/httpbin"));
			// @formatter:on
		}

		@Bean
		public RouterFunction<ServerResponse> gatewayRouterFunctionsHeaderPredicate() {
			// @formatter:off
			return route(path("/headerregex").and(header("X-MyHeader", "fo.")), http())
					.filter(new LocalServerPortUriResolver())
					.filter(setPath("/httpbin/headers"));
			// @formatter:on
		}

		@Bean
		public RouterFunction<ServerResponse> gatewayRouterFunctionsCookiePredicate() {
			// @formatter:off
			return route(path("/cookieregex").and(cookie("mycookie", "fo.")), http())
					.filter(new LocalServerPortUriResolver())
					.filter(setPath("/httpbin/headers"));
			// @formatter:on
		}

	}

	@RestController
	protected static class RetryController {

		Log log = LogFactory.getLog(getClass());

		ConcurrentHashMap<String, AtomicInteger> map = new ConcurrentHashMap<>();

		@GetMapping("/httpbin/retry")
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

		AtomicInteger getCount(String key) {
			return map.computeIfAbsent(key, s -> new AtomicInteger());
		}

	}

	protected static class TestHandler {

		public ServerResponse hello(ServerRequest request) {
			return ServerResponse.ok().body("Hello");
		}

	}

	public static class TestLoadBalancerConfig {

		@LocalServerPort
		protected int port = 0;

		@Bean
		public ServiceInstanceListSupplier staticServiceInstanceListSupplier() {
			return ServiceInstanceListSuppliers.from("testservice",
					new DefaultServiceInstance("testservice" + "-1", "testservice", "localhost", port, false));
		}

	}

}
