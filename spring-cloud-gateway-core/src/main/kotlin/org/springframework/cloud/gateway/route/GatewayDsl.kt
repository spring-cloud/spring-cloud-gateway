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

import reactor.core.publisher.Flux
import java.util.function.Predicate

/**
 * A Kotlin based DSL to configure a [RouteLocator]
 *
 * Example:
 * ```
 * val routeLocator = gateway {
 *   route(id = "test") {
 *      uri("http://httpbin.org:80")
 *      predicate(host("**.abc.org") and path("/image/png"))
 *      add(addResponseHeader("X-TestHeader", "foobar"))
 *   }
 * }
 * ```
 *
 * @author Biju Kunjummen
 * @deprecated see [org.springframework.cloud.gateway.route.builder.RouteDsl]
 */
@Deprecated("see RouteDsl")
fun gateway(routeLocator: RouteLocatorDsl.() -> Unit) = RouteLocatorDsl().apply(routeLocator).build()


/**
 * Provider for [RouteLocator] DSL functionality
 * @deprecated see [org.springframework.cloud.gateway.route.builder.RouteDsl]
 */
@Deprecated("see RouteDsl")
class RouteLocatorDsl {
	private val routes = mutableListOf<Route>()

	/**
	 * DSL to add a route to the [RouteLocator]
	 *
	 * @see [Route.Builder]
	 */
	fun route(id: String? = null, order: Int = 0, uri: String? = null, init: Route.Builder.() -> Unit) {
		val builder = Route.builder()
		if (uri != null) {
			builder.uri(uri)
		}
		routes += builder.id(id).order(order).apply(init).build()
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

	/**
	 * A helper to return a composed [Predicate] that negates this [Predicate]
	 */
	fun <T> Predicate<T>.negate() = this.negate()
}


