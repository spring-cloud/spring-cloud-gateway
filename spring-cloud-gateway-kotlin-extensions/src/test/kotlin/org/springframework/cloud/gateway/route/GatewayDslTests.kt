package org.springframework.cloud.gateway.route

import org.junit.Test
import org.springframework.cloud.gateway.filter.factory.WebFilterFactories.addResponseHeader
import org.springframework.cloud.gateway.handler.predicate.RoutePredicates.host
import org.springframework.cloud.gateway.handler.predicate.RoutePredicates.path
import org.springframework.mock.http.server.reactive.MockServerHttpRequest
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
                predicate(path("/image/webp"))
                add(addResponseHeader("X-AnotherHeader", "baz"))
                add(addResponseHeader("X-AnotherHeader-2", "baz-2"))
            }
        }

        val sampleExchange: ServerWebExchange = MockServerHttpRequest.get("/image/webp").header("Host", "test.abc.org").toExchange()
        val matchingRoute = routeLocator.routes.filter({ r -> r.predicate.test(sampleExchange) }).next()

        StepVerifier
                .create(matchingRoute)
                .expectNextMatches({ r -> 
                    r.id == "test2" && r.webFilters.size == 2 && r.uri == URI.create("http://httpbin.org:80")})
                .expectComplete()
                .verify()
    }
}