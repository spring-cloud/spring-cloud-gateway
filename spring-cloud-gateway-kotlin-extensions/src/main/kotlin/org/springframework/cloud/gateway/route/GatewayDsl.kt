package org.springframework.cloud.gateway.route

import reactor.core.publisher.Flux
import java.util.function.Predicate


class RoutesDsl {
    private val routes = mutableListOf<Route>()

    fun route(init: Route.Builder.() -> Unit) {
        routes += Route.builder().apply(init).build()
    }

    fun build(): RouteLocator {
        return RouteLocator { Flux.fromIterable(this.routes) }
    }

    infix fun <T> Predicate<T>.and(other: Predicate<T>) = this.and(other)
    infix fun <T> Predicate<T>.or(other: Predicate<T>) = this.or(other)
}

fun gateway(routes: RoutesDsl.() -> Unit) = RoutesDsl().apply(routes).build()
