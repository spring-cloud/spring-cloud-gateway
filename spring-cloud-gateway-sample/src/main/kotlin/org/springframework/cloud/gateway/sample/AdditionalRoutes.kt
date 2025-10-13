/*
 * Copyright 2017-present the original author or authors.
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

package org.springframework.cloud.gateway.sample

import org.springframework.beans.factory.annotation.Value
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder
import org.springframework.cloud.gateway.route.builder.filters
import org.springframework.cloud.gateway.route.builder.routes
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration(proxyBeanMethods = false)
open class AdditionalRoutes {

    @Value("\${test.uri:http://httpbin.org:80}")
    var uri: String = ""

    @Bean
    open fun additionalRouteLocator(builder: RouteLocatorBuilder) = builder.routes {
        route(id = "test-kotlin") {
            host("kotlin.abc.org") and path("/anything/kotlinroute")
            filters {
                prefixPath("/httpbin")
                addResponseHeader("X-TestHeader", "foobar")
            }
            uri(uri)
        }
    }

}