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
 *
 */

package org.springframework.cloud.gateway.route

import org.junit.Test
import org.springframework.cloud.gateway.filter.factory.GatewayFilters.addResponseHeader
import org.springframework.cloud.gateway.handler.predicate.RoutePredicates.host
import org.springframework.cloud.gateway.handler.predicate.RoutePredicates.path
import org.springframework.mock.http.server.reactive.MockServerHttpRequest
import org.springframework.mock.web.server.MockServerWebExchange
import org.springframework.web.server.ServerWebExchange
import reactor.test.StepVerifier
import java.net.URI

class GatewayDslTests {

	@Test
	fun sampleRouteDsl() {
		val routeLocator = gateway {
			route {
				id("test")
				uri("http://httpbin.org:80")
				predicate(host("**.abc.org") and path("/image/png"))
				add(addResponseHeader("X-TestHeader", "foobar"))
			}

			route {
				id("test2")
				uri("http://httpbin.org:80")
				predicate(path("/image/webp") or path("/image/anotherone"))
				add(addResponseHeader("X-AnotherHeader", "baz"))
				add(addResponseHeader("X-AnotherHeader-2", "baz-2"))
			}
		}

		StepVerifier
				.create(routeLocator.routes)
				.expectNextMatches({ r ->
					r.id == "test" && r.filters.size == 1 && r.uri == URI.create("http://httpbin.org:80")
				})
				.expectNextMatches({ r ->
					r.id == "test2" && r.filters.size == 2 && r.uri == URI.create("http://httpbin.org:80")
				})
				.expectComplete()
				.verify()

		val sampleExchange: ServerWebExchange = MockServerWebExchange.from(MockServerHttpRequest.get("/image/webp")
				.header("Host", "test.abc.org").build())

		val filteredRoutes = routeLocator.routes.filter({ r -> r.predicate.test(sampleExchange) })

		StepVerifier.create(filteredRoutes)
				.expectNextMatches({ r ->
					r.id == "test2" && r.filters.size == 2 && r.uri == URI.create("http://httpbin.org:80")
				})
				.expectComplete()
				.verify()
	}

	@Test
	fun dslWithFunctionParameters() {
		val routerLocator = gateway {
			route(id = "test", order = 10, uri = "http://httpbin.org") {
				predicate(host("**.abc.org"))
			}
		}

		StepVerifier.create(routerLocator.routes)
				.expectNextMatches({ r ->
					r.id == "test" &&
							r.uri == URI.create("http://httpbin.org") &&
							r.order == 10 &&
							r.order == 10 &&
							r.predicate.test(MockServerWebExchange
									.from(MockServerHttpRequest
											.get("/someuri").header("Host", "test.abc.org")))
				})
				.expectComplete()
				.verify()
	}
}