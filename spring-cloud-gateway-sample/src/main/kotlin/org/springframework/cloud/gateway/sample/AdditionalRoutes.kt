package org.springframework.cloud.gateway.sample

import org.springframework.beans.factory.annotation.Value
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder
import org.springframework.cloud.gateway.route.builder.filters
import org.springframework.cloud.gateway.route.builder.routes
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
open class AdditionalRoutes {

	@Value("\${test.uri:http://httpbin.org:80}")
	var uri: String? = null

	@Bean
	open fun additionalRouteLocator(builder: RouteLocatorBuilder) = builder.routes {
		route(id = "test-kotlin") {
			host("kotlin.abc.org") and path("/image/png")
			filters {
				prefixPath("/httpbin")
				addResponseHeader("X-TestHeader", "foobar")
			}
			uri(uri)
		}
	}

}