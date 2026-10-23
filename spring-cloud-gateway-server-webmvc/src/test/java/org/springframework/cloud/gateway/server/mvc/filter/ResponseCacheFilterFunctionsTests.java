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

package org.springframework.cloud.gateway.server.mvc.filter;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.cloud.gateway.server.mvc.config.GatewayMvcProperties;
import org.springframework.cloud.gateway.server.mvc.test.LocalServerPortUriResolver;
import org.springframework.cloud.gateway.server.mvc.test.PermitAllSecurityConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.cloud.gateway.server.mvc.filter.BeforeFilterFunctions.removeRequestHeader;
import static org.springframework.cloud.gateway.server.mvc.filter.BodyFilterFunctions.modifyResponseBody;
import static org.springframework.cloud.gateway.server.mvc.filter.FilterFunctions.prefixPath;
import static org.springframework.cloud.gateway.server.mvc.filter.ResponseCacheFilterFunctions.localResponseCache;
import static org.springframework.cloud.gateway.server.mvc.handler.GatewayRouterFunctions.route;
import static org.springframework.cloud.gateway.server.mvc.handler.HandlerFunctions.http;

/**
 * @author Ingo Griebsch
 * @author Nikita Kibitkin
 */
@SpringBootTest(
		properties = { GatewayMvcProperties.PREFIX + ".function.enabled=false",
				GatewayMvcProperties.PREFIX + ".routes[0].id=testcachedprops",
				GatewayMvcProperties.PREFIX + ".routes[0].uri=no://op",
				GatewayMvcProperties.PREFIX + ".routes[0].predicates[0]=Path=/props/cached",
				GatewayMvcProperties.PREFIX + ".routes[0].filters[0]=LocalServerPortUriResolver=",
				GatewayMvcProperties.PREFIX + ".routes[0].filters[1]=LocalResponseCache=30s,2MB",
				GatewayMvcProperties.PREFIX + ".routes[0].filters[2]=SetPath=/do/cached",
				GatewayMvcProperties.PREFIX + ".routes[1].id=testcachedprops0",
				GatewayMvcProperties.PREFIX + ".routes[1].uri=no://op",
				GatewayMvcProperties.PREFIX + ".routes[1].predicates[0]=Path=/props/cached0",
				GatewayMvcProperties.PREFIX + ".routes[1].filters[0]=LocalServerPortUriResolver=",
				GatewayMvcProperties.PREFIX + ".routes[1].filters[1]=LocalResponseCache",
				GatewayMvcProperties.PREFIX + ".routes[1].filters[2]=SetPath=/do/cached",
				GatewayMvcProperties.PREFIX + ".routes[2].id=testcachedprops1",
				GatewayMvcProperties.PREFIX + ".routes[2].uri=no://op",
				GatewayMvcProperties.PREFIX + ".routes[2].predicates[0]=Path=/props/cached1",
				GatewayMvcProperties.PREFIX + ".routes[2].filters[0]=LocalServerPortUriResolver=",
				GatewayMvcProperties.PREFIX + ".routes[2].filters[1]=LocalResponseCache=90s",
				GatewayMvcProperties.PREFIX + ".routes[2].filters[2]=SetPath=/do/cached" },
		webEnvironment = WebEnvironment.RANDOM_PORT)
public class ResponseCacheFilterFunctionsTests {

	@Autowired
	RestTestClient restClient;

	@Autowired
	TestConfiguration.CacheController cacheController;

	@Test
	public void responseIsServedFromCache() {
		restClient.get()
			.uri("/cached?key=cache-works")
			.exchange()
			.expectStatus()
			.isOk()
			.expectBody(String.class)
			.isEqualTo("1");
		restClient.get()
			.uri("/cached?key=cache-works")
			.exchange()
			.expectStatus()
			.isOk()
			.expectHeader()
			.valueMatches(HttpHeaders.CACHE_CONTROL, "max-age=\\d+")
			.expectHeader()
			.doesNotExist(HttpHeaders.PRAGMA)
			.expectHeader()
			.doesNotExist(HttpHeaders.EXPIRES)
			.expectBody(String.class)
			.isEqualTo("1");
	}

	@Test
	public void noCacheRequestRevalidatesAndKeepsCacheEntry() {
		restClient.get()
			.uri("/cached?key=no-cache")
			.exchange()
			.expectStatus()
			.isOk()
			.expectBody(String.class)
			.isEqualTo("1");
		restClient.get()
			.uri("/cached?key=no-cache")
			.header(HttpHeaders.CACHE_CONTROL, "no-cache")
			.exchange()
			.expectStatus()
			.isOk()
			.expectBody(String.class)
			.isEqualTo("2");
		restClient.get()
			.uri("/cached?key=no-cache")
			.exchange()
			.expectStatus()
			.isOk()
			.expectBody(String.class)
			.isEqualTo("1");
	}

	@Test
	public void requestWithNoStoreIsNotCached() {
		restClient.get()
			.uri("/cached?key=no-store")
			.header(HttpHeaders.CACHE_CONTROL, "no-store")
			.exchange()
			.expectStatus()
			.isOk()
			.expectBody(String.class)
			.isEqualTo("1");
		restClient.get()
			.uri("/cached?key=no-store")
			.header(HttpHeaders.CACHE_CONTROL, "no-store")
			.exchange()
			.expectStatus()
			.isOk()
			.expectBody(String.class)
			.isEqualTo("2");
	}

	@Test
	public void postRequestIsNotCached() {
		restClient.post()
			.uri("/cachedpost?key=post")
			.exchange()
			.expectStatus()
			.isOk()
			.expectBody(String.class)
			.isEqualTo("1");
		restClient.post()
			.uri("/cachedpost?key=post")
			.exchange()
			.expectStatus()
			.isOk()
			.expectBody(String.class)
			.isEqualTo("2");
	}

	@Test
	public void privateResponseIsNotCached() {
		restClient.get()
			.uri("/private?key=private")
			.exchange()
			.expectStatus()
			.isOk()
			.expectBody(String.class)
			.isEqualTo("1");
		restClient.get()
			.uri("/private?key=private")
			.exchange()
			.expectStatus()
			.isOk()
			.expectBody(String.class)
			.isEqualTo("2");
	}

	@Test
	public void varyWildcardResponseIsNotCached() {
		restClient.get()
			.uri("/varystar?key=varystar")
			.exchange()
			.expectStatus()
			.isOk()
			.expectBody(String.class)
			.isEqualTo("1");
		restClient.get()
			.uri("/varystar?key=varystar")
			.exchange()
			.expectStatus()
			.isOk()
			.expectBody(String.class)
			.isEqualTo("2");
	}

	@Test
	public void varyHeaderProducesSeparateCacheEntries() {
		restClient.get()
			.uri("/vary?key=vary")
			.header("X-Custom", "one")
			.exchange()
			.expectStatus()
			.isOk()
			.expectBody(String.class)
			.isEqualTo("1");
		restClient.get()
			.uri("/vary?key=vary")
			.header("X-Custom", "two")
			.exchange()
			.expectStatus()
			.isOk()
			.expectBody(String.class)
			.isEqualTo("2");
		restClient.get()
			.uri("/vary?key=vary")
			.header("X-Custom", "one")
			.exchange()
			.expectStatus()
			.isOk()
			.expectBody(String.class)
			.isEqualTo("1");
	}

	@Test
	public void nonVaryHeaderDoesNotProduceSeparateCacheEntries() {
		restClient.get()
			.uri("/cached?key=non-vary")
			.header("X-Custom", "one")
			.exchange()
			.expectStatus()
			.isOk()
			.expectBody(String.class)
			.isEqualTo("1");
		restClient.get()
			.uri("/cached?key=non-vary")
			.header("X-Custom", "two")
			.exchange()
			.expectStatus()
			.isOk()
			.expectBody(String.class)
			.isEqualTo("1");
	}

	@Test
	public void propertiesDefinedRouteIsCached() {
		restClient.get()
			.uri("/props/cached?key=props")
			.exchange()
			.expectStatus()
			.isOk()
			.expectBody(String.class)
			.isEqualTo("1");
		restClient.get()
			.uri("/props/cached?key=props")
			.exchange()
			.expectStatus()
			.isOk()
			.expectBody(String.class)
			.isEqualTo("1");
	}

	@Test
	public void propertiesDefinedRouteWithoutArgsIsCached() {
		restClient.get()
			.uri("/props/cached0?key=props0")
			.exchange()
			.expectStatus()
			.isOk()
			.expectBody(String.class)
			.isEqualTo("1");
		restClient.get()
			.uri("/props/cached0?key=props0")
			.exchange()
			.expectStatus()
			.isOk()
			.expectBody(String.class)
			.isEqualTo("1");
	}

	@Test
	public void propertiesDefinedRouteWithOnlyTimeToLiveIsCached() {
		restClient.get()
			.uri("/props/cached1?key=props1")
			.exchange()
			.expectStatus()
			.isOk()
			.expectBody(String.class)
			.isEqualTo("1");
		restClient.get()
			.uri("/props/cached1?key=props1")
			.exchange()
			.expectStatus()
			.isOk()
			.expectBody(String.class)
			.isEqualTo("1");
	}

	@Test
	public void modifiedResponseBodyIsCachedAndServedConsistently() {
		restClient.get()
			.uri("/modified?key=modify")
			.exchange()
			.expectStatus()
			.isOk()
			.expectBody(String.class)
			.isEqualTo("1-modified");
		restClient.get()
			.uri("/modified?key=modify")
			.exchange()
			.expectStatus()
			.isOk()
			.expectBody(String.class)
			.isEqualTo("1-modified");
	}

	@Test
	public void streamingResponseIsNotCached() {
		restClient.get()
			.uri("/stream?key=stream")
			.exchange()
			.expectStatus()
			.isOk()
			.expectBody(String.class)
			.isEqualTo("data: 1\n\n");
		restClient.get()
			.uri("/stream?key=stream")
			.exchange()
			.expectStatus()
			.isOk()
			.expectBody(String.class)
			.isEqualTo("data: 2\n\n");
	}

	@Test
	public void replacedResponseIsNeverMixedWithUpstreamBody() {
		// a filter between the cache and the proxy replaces the failing upstream
		// response (e.g. a circuit breaker fallback); the cache must never pair the
		// replacement status with the discarded upstream body
		restClient.get()
			.uri("/fallback?key=fallback")
			.exchange()
			.expectStatus()
			.isOk()
			.expectBody(String.class)
			.isEqualTo("fallback");
		restClient.get()
			.uri("/fallback?key=fallback")
			.exchange()
			.expectStatus()
			.isOk()
			.expectHeader()
			.valueMatches(HttpHeaders.CONTENT_TYPE, "text/plain.*")
			.expectBody(String.class)
			.isEqualTo("fallback");
		// the second request was served from the cache: the upstream was hit once
		assertThat(cacheController.map.get("fallback").get()).isEqualTo(1);
	}

	@Test
	public void conditionalRequestDoesNotPoisonCache() {
		// the upstream ignores conditional headers (they are stripped before
		// proxying) and always replies 200 with an ETag; the gateway itself answers
		// 304 to the matching conditional request and must not cache the bodiless
		// write under the advertised 200
		restClient.get()
			.uri("/etag?key=etag")
			.header(HttpHeaders.IF_NONE_MATCH, "\"v1\"")
			.exchange()
			.expectStatus()
			.isEqualTo(HttpStatus.NOT_MODIFIED);
		restClient.get().uri("/etag?key=etag").exchange().expectStatus().isOk().expectBody(String.class).isEqualTo("2");
		restClient.get().uri("/etag?key=etag").exchange().expectStatus().isOk().expectBody(String.class).isEqualTo("2");
	}

	@Test
	public void conditionalRequestIsAnsweredFromCacheWithNotModified() {
		// once the response is cached, a matching conditional request revalidates
		// against the cached ETag: the gateway answers 304 and keeps the entry
		restClient.get()
			.uri("/etag?key=etag-hit")
			.exchange()
			.expectStatus()
			.isOk()
			.expectBody(String.class)
			.isEqualTo("1");
		restClient.get()
			.uri("/etag?key=etag-hit")
			.header(HttpHeaders.IF_NONE_MATCH, "\"v1\"")
			.exchange()
			.expectStatus()
			.isEqualTo(HttpStatus.NOT_MODIFIED);
		restClient.get()
			.uri("/etag?key=etag-hit")
			.exchange()
			.expectStatus()
			.isOk()
			.expectBody(String.class)
			.isEqualTo("1");
		// both cache hits: the upstream was hit only by the priming request
		assertThat(cacheController.map.get("etag-hit").get()).isEqualTo(1);
	}

	@SpringBootConfiguration
	@EnableAutoConfiguration
	@Import(PermitAllSecurityConfiguration.class)
	protected static class TestConfiguration {

		@Bean
		public RouterFunction<ServerResponse> gatewayRouterFunctionsCached() {
			// @formatter:off
			return route("testcached")
					.GET("/cached", http())
					.before(new LocalServerPortUriResolver())
					.filter(localResponseCache(Duration.ofSeconds(30), null))
					.filter(prefixPath("/do"))
					.build();
			// @formatter:on
		}

		@Bean
		public RouterFunction<ServerResponse> gatewayRouterFunctionsCachedPost() {
			// @formatter:off
			return route("testcachedpost")
					.POST("/cachedpost", http())
					.before(new LocalServerPortUriResolver())
					.filter(localResponseCache(Duration.ofSeconds(30), null))
					.filter(prefixPath("/do"))
					.build();
			// @formatter:on
		}

		@Bean
		public RouterFunction<ServerResponse> gatewayRouterFunctionsCachedPrivate() {
			// @formatter:off
			return route("testcachedprivate")
					.GET("/private", http())
					.before(new LocalServerPortUriResolver())
					.filter(localResponseCache(Duration.ofSeconds(30), null))
					.filter(prefixPath("/do"))
					.build();
			// @formatter:on
		}

		@Bean
		public RouterFunction<ServerResponse> gatewayRouterFunctionsCachedVaryStar() {
			// @formatter:off
			return route("testcachedvarystar")
					.GET("/varystar", http())
					.before(new LocalServerPortUriResolver())
					.filter(localResponseCache(Duration.ofSeconds(30), null))
					.filter(prefixPath("/do"))
					.build();
			// @formatter:on
		}

		@Bean
		public RouterFunction<ServerResponse> gatewayRouterFunctionsCachedVary() {
			// @formatter:off
			return route("testcachedvary")
					.GET("/vary", http())
					.before(new LocalServerPortUriResolver())
					.filter(localResponseCache(Duration.ofSeconds(30), null))
					.filter(prefixPath("/do"))
					.build();
			// @formatter:on
		}

		@Bean
		public RouterFunction<ServerResponse> gatewayRouterFunctionsCachedModify() {
			// @formatter:off
			return route("testcachedmodify")
					.GET("/modified", http())
					.before(new LocalServerPortUriResolver())
					.filter(localResponseCache(Duration.ofSeconds(30), null))
					.filter(prefixPath("/do"))
					.after(modifyResponseBody(String.class, String.class, null,
							(request, response, body) -> body + "-modified"))
					.build();
			// @formatter:on
		}

		@Bean
		public RouterFunction<ServerResponse> gatewayRouterFunctionsCachedStream() {
			// @formatter:off
			return route("testcachedstream")
					.GET("/stream", http())
					.before(new LocalServerPortUriResolver())
					.filter(localResponseCache(Duration.ofSeconds(30), null))
					.filter(prefixPath("/do"))
					.build();
			// @formatter:on
		}

		@Bean
		public RouterFunction<ServerResponse> gatewayRouterFunctionsCachedEtag() {
			// @formatter:off
			return route("testcachedetag")
					.GET("/etag", http())
					.before(new LocalServerPortUriResolver())
					.before(removeRequestHeader(HttpHeaders.IF_NONE_MATCH))
					.filter(localResponseCache(Duration.ofSeconds(30), null))
					.filter(prefixPath("/do"))
					.build();
			// @formatter:on
		}

		@Bean
		public RouterFunction<ServerResponse> gatewayRouterFunctionsCachedFallback() {
			// @formatter:off
			return route("testcachedfallback")
					.GET("/fallback", http())
					.before(new LocalServerPortUriResolver())
					.filter(localResponseCache(Duration.ofSeconds(30), null))
					.filter((request, next) -> {
						ServerResponse response = next.handle(request);
						if (response.statusCode().is5xxServerError()) {
							return ServerResponse.ok().body("fallback");
						}
						return response;
					})
					.filter(prefixPath("/do"))
					.build();
			// @formatter:on
		}

		@RestController
		protected static class CacheController {

			ConcurrentHashMap<String, AtomicInteger> map = new ConcurrentHashMap<>();

			@GetMapping("/do/cached")
			public ResponseEntity<String> cached(@RequestParam("key") String key) {
				return ResponseEntity.ok()
					.header(HttpHeaders.PRAGMA, "no-cache")
					.header(HttpHeaders.EXPIRES, "0")
					.body(next(key));
			}

			@PostMapping("/do/cachedpost")
			public ResponseEntity<String> cachedPost(@RequestParam("key") String key) {
				return ResponseEntity.ok(next(key));
			}

			@GetMapping("/do/private")
			public ResponseEntity<String> cachedPrivate(@RequestParam("key") String key) {
				return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "private").body(next(key));
			}

			@GetMapping("/do/varystar")
			public ResponseEntity<String> varyStar(@RequestParam("key") String key) {
				return ResponseEntity.ok().header(HttpHeaders.VARY, "*").body(next(key));
			}

			@GetMapping("/do/vary")
			public ResponseEntity<String> vary(@RequestParam("key") String key,
					@RequestHeader(name = "X-Custom", required = false) String custom) {
				assertThat(custom).isNotNull();
				return ResponseEntity.ok().header(HttpHeaders.VARY, "X-Custom").body(next(key));
			}

			@GetMapping("/do/modified")
			public ResponseEntity<String> modified(@RequestParam("key") String key) {
				return ResponseEntity.ok(next(key));
			}

			@GetMapping("/do/stream")
			public ResponseEntity<String> stream(@RequestParam("key") String key) {
				return ResponseEntity.ok().contentType(MediaType.TEXT_EVENT_STREAM).body("data: " + next(key) + "\n\n");
			}

			@GetMapping("/do/etag")
			public ResponseEntity<String> etag(@RequestParam("key") String key) {
				return ResponseEntity.ok().eTag("\"v1\"").body(next(key));
			}

			@GetMapping("/do/fallback")
			public ResponseEntity<String> fallback(@RequestParam("key") String key) {
				return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(next(key));
			}

			private String next(String key) {
				return String.valueOf(map.computeIfAbsent(key, s -> new AtomicInteger()).incrementAndGet());
			}

		}

	}

}
