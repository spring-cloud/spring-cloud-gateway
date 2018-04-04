/*
 * Copyright 2013-2017 the original author or authors.
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

package org.springframework.cloud.gateway.route.builder

import org.junit.Test
import org.junit.runner.RunWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Configuration
import org.springframework.mock.http.server.reactive.MockServerHttpRequest
import org.springframework.mock.web.server.MockServerWebExchange
import org.springframework.test.context.junit4.SpringRunner
import org.springframework.web.server.ServerWebExchange
import reactor.test.StepVerifier
import java.net.URI

@RunWith(SpringRunner::class)
@SpringBootTest(classes = arrayOf(Config::class))
class RouteDslTests {

	@Autowired
	lateinit var builder: RouteLocatorBuilder

	@Test
	fun sampleRouteDsl() {
		val routeLocator = builder.routes {
			route(id = "test") {
				host("**.abc.org") and path("/image/png")
				filters {
					addResponseHeader("X-TestHeader", "foobar")
				}
				uri("http://httpbin.org:80")
			}

			route(id = "test2") {
				path("/image/webp") or path("/image/anotherone")
				filters {
					addResponseHeader("X-AnotherHeader", "baz")
					addResponseHeader("X-AnotherHeader-2", "baz-2")
				}
				uri("https://httpbin.org:443")
			}
		}

		StepVerifier
				.create(routeLocator.routes)
				.expectNextMatches({ r ->
					r.id == "test" && r.filters.size == 1 && r.uri == URI.create("http://httpbin.org:80")
				})
				.expectNextMatches({ r ->
					r.id == "test2" && r.filters.size == 2 && r.uri == URI.create("https://httpbin.org:443")
				})
				.expectComplete()
				.verify()

		val sampleExchange: ServerWebExchange = MockServerWebExchange.from(MockServerHttpRequest.get("/image/webp")
				.header("Host", "test.abc.org").build())

		val filteredRoutes = routeLocator.routes.filter({ r -> r.predicate.test(sampleExchange) })

		StepVerifier.create(filteredRoutes)
				.expectNextMatches({ r ->
					r.id == "test2" && r.filters.size == 2 && r.uri == URI.create("https://httpbin.org:443")
				})
				.expectComplete()
				.verify()
	}

	@Test
	fun dslWithFunctionParameters() {
		val routerLocator = builder.routes {
			route(id = "test1", order = 10, uri = "http://httpbin.org") {
				host("**.abc.org")
			}
			route(id = "test2", order = 10, uri = "http://someurl") {
				host("**.abc.org")
				uri("http://override-url")
			}
		}

		StepVerifier.create(routerLocator.routes)
				.expectNextMatches({ r ->
					r.id == "test1" &&
							r.uri == URI.create("http://httpbin.org:80") &&
							r.order == 10 &&
							r.predicate.test(MockServerWebExchange
									.from(MockServerHttpRequest
											.get("/someuri").header("Host", "test.abc.org")))
				})
				.expectNextMatches({ r ->
					r.id == "test2" &&
							r.uri == URI.create("http://override-url:80") &&
							r.order == 10 &&
							r.predicate.test(MockServerWebExchange
									.from(MockServerHttpRequest
											.get("/someuri").header("Host", "test.abc.org")))
				})
				.expectComplete()
				.verify()
	}
}

@Configuration
@EnableAutoConfiguration
open class Config {}