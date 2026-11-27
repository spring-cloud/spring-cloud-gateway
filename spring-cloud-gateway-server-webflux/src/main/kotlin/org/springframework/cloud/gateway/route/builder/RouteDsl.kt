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

package org.springframework.cloud.gateway.route.builder

import org.springframework.cloud.gateway.filter.factory.rewrite.RewriteFunction
import org.springframework.cloud.gateway.route.Route
import org.springframework.cloud.gateway.route.RouteLocator
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder.RouteSpec
import org.springframework.core.ParameterizedTypeReference
import java.util.function.Predicate

/**
 * A Kotlin based DSL to configure a [RouteLocator]
 *
 * Example:
 * ```
 * @Autowired
 * lateinit var builder: RouteLocatorBuilder
 *
 * val routeLocator = builder.routes {
 *   route(id = "test") {
 *		host("**.abc.org") and path("/image/png")
 *		filters {
 *			add(addResponseHeader("X-TestHeader", "foobar"))
 *	    }
 *		uri("http://httpbin.org:80")
 *   }
 * }
 * ```
 *
 * @author Biju Kunjummen
 * @author Spencer Gibb
 */
fun RouteLocatorBuilder.routes(routeLocator: RouteLocatorDsl.() -> Unit): RouteLocator {
    return RouteLocatorDsl(this).apply(routeLocator).build()
}


/**
 * Provider for [RouteLocator] DSL functionality
 */
class RouteLocatorDsl(val builder: RouteLocatorBuilder) {
    private val routes = builder.routes()

    /**
     * DSL to add a route to the [RouteLocator]
     *
     * @see [Route.Builder]
     */
    fun route(id: String? = null, order: Int = 0, uri: String? = null, init: PredicateSpec.() -> Unit) {
        val predicateSpec = if (id == null) {
            RouteSpec(routes).randomId()
        } else {
            RouteSpec(routes).id(id)
        }
        predicateSpec.order(order)
        if (uri != null) {
            predicateSpec.uri(uri)
        }

        predicateSpec.apply(init)

        val route: Route.AsyncBuilder = predicateSpec.routeBuilder
        routes.add(route)
    }

    fun build(): RouteLocator {
        return routes.build()
    }

    /**
     * A helper to return a composed [Predicate] that tests against this [Predicate] AND the [other] predicate
     */
    infix fun BooleanSpec.and(other: BooleanSpec) =
            this.routeBuilder.asyncPredicate(this.predicate.and(other.predicate))

    /**
     * A helper to return a composed [Predicate] that tests against this [Predicate] OR the [other] predicate
     */
    infix fun BooleanSpec.or(other: BooleanSpec) =
            this.routeBuilder.asyncPredicate(this.predicate.or(other.predicate))


}

/**
 * Extension method to add filters {} block to dsl
 */
fun PredicateSpec.filters(init: GatewayFilterSpec.() -> Unit) {
    val spec = createGatewayFilterSpec()
    spec.apply(init)
}

/**
 * Extension for [GatewayFilterSpec.modifyRequestBody] providing a
 * `modifyRequestBody<T, R> { exchange, body -> ... }` variant leveraging Kotlin reified type parameters.
 *
 * The body passed to [rewriteFunction] is `null` when the request has no body.
 */
inline fun <reified T : Any, reified R : Any> GatewayFilterSpec.modifyRequestBody(
    rewriteFunction: RewriteFunction<T, R>
): GatewayFilterSpec = modifyRequestBody(
    object : ParameterizedTypeReference<T>() {},
    object : ParameterizedTypeReference<R>() {},
    rewriteFunction
)

/**
 * Extension for [GatewayFilterSpec.modifyRequestBody] providing a
 * `modifyRequestBody<T, R>(newContentType) { exchange, body -> ... }` variant leveraging Kotlin reified type
 * parameters.
 *
 * The body passed to [rewriteFunction] is `null` when the request has no body.
 */
inline fun <reified T : Any, reified R : Any> GatewayFilterSpec.modifyRequestBody(
    newContentType: String?,
    rewriteFunction: RewriteFunction<T, R>
): GatewayFilterSpec = modifyRequestBody(
    object : ParameterizedTypeReference<T>() {},
    object : ParameterizedTypeReference<R>() {},
    newContentType,
    rewriteFunction
)

/**
 * Extension for [GatewayFilterSpec.modifyResponseBody] providing a
 * `modifyResponseBody<T, R> { exchange, body -> ... }` variant leveraging Kotlin reified type parameters.
 *
 * The body passed to [rewriteFunction] is `null` when the response has no body. Only the raw classes of
 * [T] and [R] are used, so generic type arguments are not taken into account (a `List<Foo>` body is read
 * as a list of maps).
 */
inline fun <reified T : Any, reified R : Any> GatewayFilterSpec.modifyResponseBody(
    rewriteFunction: RewriteFunction<T, R>
): GatewayFilterSpec = modifyResponseBody(T::class.java, R::class.java, rewriteFunction)

/**
 * Extension for [GatewayFilterSpec.modifyResponseBody] providing a
 * `modifyResponseBody<T, R>(newContentType) { exchange, body -> ... }` variant leveraging Kotlin reified type
 * parameters.
 *
 * The body passed to [rewriteFunction] is `null` when the response has no body. Only the raw classes of
 * [T] and [R] are used, so generic type arguments are not taken into account (a `List<Foo>` body is read
 * as a list of maps).
 */
inline fun <reified T : Any, reified R : Any> GatewayFilterSpec.modifyResponseBody(
    newContentType: String,
    rewriteFunction: RewriteFunction<T, R>
): GatewayFilterSpec = modifyResponseBody(T::class.java, R::class.java, newContentType, rewriteFunction)

/**
 * Extension for [PredicateSpec.readBody] providing a `readBody<T> { body -> ... }` variant leveraging Kotlin
 * reified type parameters.
 *
 * Only the raw class of [T] is used, so generic type arguments are not taken into account.
 */
inline fun <reified T : Any> PredicateSpec.readBody(predicate: Predicate<T>): BooleanSpec =
    readBody(T::class.java, predicate)
