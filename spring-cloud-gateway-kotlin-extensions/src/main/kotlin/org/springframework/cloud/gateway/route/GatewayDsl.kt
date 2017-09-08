package org.springframework.cloud.gateway.route

import reactor.core.publisher.Flux
import java.util.function.Predicate

/**
 * A Kotlin based DSL to configure a [RouteLocator]
 * 
 * Example:
 * ```
 * val routeLocator = gateway {
 *   route {
 *      id("test")
 *      uri("http://httpbin.org:80")
 *      predicate(host("**.abc.org") and path("/image/png"))
 *      add(addResponseHeader("X-TestHeader", "foobar"))
 *   }
 * }
 * ```
 * 
 * @author Biju Kunjummen
 */
fun gateway(routeLocator: RouteLocatorDsl.() -> Unit) = RouteLocatorDsl().apply(routeLocator).build()


/**
 * Provider for [RouteLocator] DSL functionality
 */
class RouteLocatorDsl {
    private val routes = mutableListOf<Route>()

    /**
     * DSL to add a route to the [RouteLocator]
     * 
     * @see [Route.Builder]
     */
    fun route(init: Route.Builder.() -> Unit) {
        routes += Route.builder().apply(init).build()
    }

    fun build(): RouteLocator {
        return RouteLocator { Flux.fromIterable(this.routes) }
    }

    /**
     * A helper to return a composed [Predicate] that tests against this [Predicate] AND the [other] predicate
     */
    infix fun <T> Predicate<T>.and(other: Predicate<T>) = this.and(other)

    /**
     * A helper to return a composed [Predicate] that tests against this [Predicate] OR the [other] predicate
     */
    infix fun <T> Predicate<T>.or(other: Predicate<T>) = this.or(other)
}


