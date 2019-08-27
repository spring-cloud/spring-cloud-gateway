/*
 * Copyright 2013-2019 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *	  https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.cloud.gateway.route.builder

import org.springframework.cloud.gateway.filter.factory.rewrite.RewriteFunction

/**
 * Idiomatic extension methods, which make use of reified type parameters
 * @author Igor Perikov
 */
inline fun <reified T, reified R> GatewayFilterSpec.modifyRequestBody(
    newContentType: String,
    rewriteFunction: RewriteFunction<T, R>
): GatewayFilterSpec {
    return this.modifyRequestBody(T::class.java, R::class.java, newContentType, rewriteFunction)
}

inline fun <reified T, reified R> GatewayFilterSpec.modifyRequestBody(
    rewriteFunction: RewriteFunction<T, R>
): GatewayFilterSpec {
    return this.modifyRequestBody(T::class.java, R::class.java, rewriteFunction)
}

inline fun <reified T, reified R> GatewayFilterSpec.modifyResponseBody(
    newContentType: String,
    rewriteFunction: RewriteFunction<T, R>
): GatewayFilterSpec {
    return this.modifyResponseBody(T::class.java, R::class.java, newContentType, rewriteFunction)
}

inline fun <reified T, reified R> GatewayFilterSpec.modifyResponseBody(
    rewriteFunction: RewriteFunction<T, R>
): GatewayFilterSpec {
    return this.modifyResponseBody(T::class.java, R::class.java, rewriteFunction)
}
