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
	fun testSampleRouteDsl() {
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
}