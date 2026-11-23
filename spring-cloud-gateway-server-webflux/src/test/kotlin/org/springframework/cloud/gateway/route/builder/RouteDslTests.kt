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

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT
import org.springframework.cloud.gateway.route.RouteLocator
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils
import org.springframework.cloud.gateway.test.BaseWebClientTests
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.mock.http.server.reactive.MockServerHttpRequest
import org.springframework.mock.web.server.MockServerWebExchange
import org.springframework.test.annotation.DirtiesContext
import org.springframework.web.reactive.function.BodyInserters
import org.springframework.web.server.ServerWebExchange
import reactor.core.publisher.Mono
import reactor.kotlin.core.publisher.toMono
import reactor.test.StepVerifier
import java.net.URI

@SpringBootTest(classes = [Config::class], webEnvironment = RANDOM_PORT)
@DirtiesContext
class RouteDslTests : BaseWebClientTests() {

	@Autowired
	lateinit var builder: RouteLocatorBuilder

	@Test
	fun sampleRouteDsl() {
		val routeLocator = builder.routes {
			route(id = "test") {
				host("**.abc.org") and path("/image/png")
				filters {
					addResponseHeader("X-TestHeader", "foobar")
				}
				uri("http://httpbin.org:80")
			}

			route(id = "test2") {
				path("/image/webp") or path("/image/anotherone")
				filters {
					addResponseHeader("X-AnotherHeader", "baz")
					addResponseHeader("X-AnotherHeader-2", "baz-2")
				}
				uri("https://httpbin.org:443")
			}
		}

		StepVerifier
				.create(routeLocator.routes)
				.expectNextMatches({
					it.id == "test" && it.filters.size == 1 && it.uri == URI.create("http://httpbin.org:80")
				})
				.expectNextMatches({
					it.id == "test2" && it.filters.size == 2 && it.uri == URI.create("https://httpbin.org:443")
				})
				.expectComplete()
				.verify()

		val sampleExchange: ServerWebExchange = MockServerWebExchange.from(MockServerHttpRequest.get("/image/webp")
				.header("Host", "test.abc.org").build())

		val filteredRoutes = routeLocator.routes.filter({
			sampleExchange.attributes.put(ServerWebExchangeUtils.GATEWAY_PREDICATE_ROUTE_ATTR, it.id)
            it.predicate.apply(sampleExchange).toMono().block() == true
		})

		StepVerifier.create(filteredRoutes)
				.expectNextMatches({
					it.id == "test2" && it.filters.size == 2 && it.uri == URI.create("https://httpbin.org:443")
				})
				.expectComplete()
				.verify()
	}

	@Test
	fun dslWithFunctionParameters() {
		val routerLocator = builder.routes {
			route(id = "test1", order = 10, uri = "http://httpbin.org") {
				host("**.abc.org")
			}
			route(id = "test2", order = 10, uri = "http://someurl") {
				host("**.abc.org")
				uri("http://override-url")
			}
		}

		StepVerifier.create(routerLocator.routes)
				.expectNextMatches({
					it.id == "test1" &&
							it.uri == URI.create("http://httpbin.org:80") &&
							it.order == 10 &&
                            it.predicate.apply(MockServerWebExchange
                                .from(MockServerHttpRequest
                                    .get("/someuri").header("Host", "test.abc.org")))
                                .toMono().block() == true
				})
				.expectNextMatches({
					it.id == "test2" &&
							it.uri == URI.create("http://override-url:80") &&
							it.order == 10 &&
                            it.predicate.apply(MockServerWebExchange
                                .from(MockServerHttpRequest
                                    .get("/someuri").header("Host", "test.abc.org")))
                                .toMono().block() == true
				})
				.expectComplete()
				.verify()
	}

	@Test
	fun modifyRequestBodyWithReifiedTypes() {
		testClient.post()
				.uri("/post")
				.header("Host", "www.kotlinmodifyrequestbody.org")
				.header(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_PLAIN_VALUE)
				.body(BodyInserters.fromValue("hello"))
				.exchange()
				.expectStatus().isOk
				.expectBody()
				.jsonPath("headers.Content-Type").isEqualTo(MediaType.TEXT_PLAIN_VALUE)
				.jsonPath("data").isEqualTo("HELLO")
	}

	@Test
	fun modifyRequestBodyWithReifiedTypesAndContentType() {
		testClient.post()
				.uri("/post")
				.header("Host", "www.kotlinmodifyrequestbodycontenttype.org")
				.header(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_PLAIN_VALUE)
				.body(BodyInserters.fromValue("hello"))
				.exchange()
				.expectStatus().isOk
				.expectBody()
				.jsonPath("headers.Content-Type").isEqualTo(MediaType.APPLICATION_JSON_VALUE)
				.jsonPath("data").isEqualTo("{\"value\":\"hello\"}")
	}

	@Test
	fun modifyRequestBodyWithReifiedTypesAndNoBody() {
		testClient.post()
				.uri("/post")
				.header("Host", "www.kotlinmodifyrequestbodyempty.org")
				.header(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_PLAIN_VALUE)
				.exchange()
				.expectStatus().isOk
				.expectBody()
				.jsonPath("data").isEqualTo("empty")
	}

	@Test
	fun modifyRequestBodyWithReifiedGenericTypes() {
		testClient.post()
				.uri("/post")
				.header("Host", "www.kotlinmodifyrequestbodygeneric.org")
				.header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
				.body(BodyInserters.fromValue("[{\"name\":\"foo\"},{\"name\":\"bar\"}]"))
				.exchange()
				.expectStatus().isOk
				.expectBody()
				.jsonPath("data").isEqualTo("foo,bar")
	}

	@Test
	fun modifyResponseBodyWithReifiedTypes() {
		testClient.get()
				.uri("/")
				.header("Host", "www.kotlinmodifyresponsebody.org")
				.accept(MediaType.APPLICATION_JSON)
				.exchange()
				.expectStatus().isOk
				.expectBody()
				.json("{\"value\": \"httpbin compatible home\", \"length\": 23}")
	}

	@Test
	fun modifyResponseBodyWithReifiedTypesAndContentType() {
		testClient.get()
				.uri("/")
				.header("Host", "www.kotlinmodifyresponsebodycontenttype.org")
				.accept(MediaType.ALL)
				.exchange()
				.expectStatus().isOk
				.expectHeader().valueEquals(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_PLAIN_VALUE)
				.expectBody(String::class.java).isEqualTo("HTTPBIN COMPATIBLE HOME")
	}

	@Test
	fun readBodyWithReifiedType() {
		testClient.post()
				.uri("/post")
				.header("Host", "www.kotlinreadbody.org")
				.header(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_PLAIN_VALUE)
				.body(BodyInserters.fromValue("kotlin"))
				.exchange()
				.expectStatus().isOk
				.expectBody()
				.jsonPath("headers.X-Read-Body").isEqualTo("matched")
				.jsonPath("data").isEqualTo("kotlin")

		testClient.post()
				.uri("/post")
				.header("Host", "www.kotlinreadbody.org")
				.header(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_PLAIN_VALUE)
				.body(BodyInserters.fromValue("java"))
				.exchange()
				.expectStatus().isOk
				.expectHeader().valueEquals(ROUTE_ID_HEADER, "default_path_to_httpbin")
				.expectBody()
				.jsonPath("headers.X-Read-Body").doesNotExist()
	}
}

@Configuration(proxyBeanMethods = false)
@EnableAutoConfiguration
@Import(BaseWebClientTests.DefaultTestConfig::class)
open class Config {

	@Bean
	open fun kotlinDslRouteLocator(builder: RouteLocatorBuilder, @Value("\${test.uri}") uri: String): RouteLocator {
		return builder.routes {
			route(id = "kotlin_modify_request_body") {
				host("www.kotlinmodifyrequestbody.org")
				filters {
					modifyRequestBody<String, String> { _, body -> Mono.just(body!!.uppercase()) }
				}
				uri(uri)
			}
			route(id = "kotlin_modify_request_body_content_type") {
				host("www.kotlinmodifyrequestbodycontenttype.org")
				filters {
					modifyRequestBody<String, Map<*, *>>(MediaType.APPLICATION_JSON_VALUE) { _, body ->
						Mono.just(mapOf("value" to body))
					}
				}
				uri(uri)
			}
			route(id = "kotlin_modify_request_body_empty") {
				host("www.kotlinmodifyrequestbodyempty.org")
				filters {
					modifyRequestBody<String, String> { _, body -> Mono.just(body ?: "empty") }
				}
				uri(uri)
			}
			route(id = "kotlin_modify_request_body_generic") {
				host("www.kotlinmodifyrequestbodygeneric.org")
				filters {
					modifyRequestBody<List<Greeting>, String>(MediaType.TEXT_PLAIN_VALUE) { _, body ->
						Mono.just(body!!.joinToString(",") { it.name })
					}
				}
				uri(uri)
			}
			route(id = "kotlin_modify_response_body") {
				path("/") and host("www.kotlinmodifyresponsebody.org")
				filters {
					prefixPath("/httpbin")
					modifyResponseBody<String, Map<*, *>> { _, body ->
						Mono.just(mapOf("value" to body, "length" to body!!.length))
					}
				}
				uri(uri)
			}
			route(id = "kotlin_modify_response_body_content_type") {
				path("/") and host("www.kotlinmodifyresponsebodycontenttype.org")
				filters {
					prefixPath("/httpbin")
					modifyResponseBody<String, String>(MediaType.TEXT_PLAIN_VALUE) { _, body ->
						Mono.just(body!!.uppercase())
					}
				}
				uri(uri)
			}
			route(id = "kotlin_read_body") {
				host("www.kotlinreadbody.org") and readBody<String> { it == "kotlin" }
				filters {
					addRequestHeader("X-Read-Body", "matched")
				}
				uri(uri)
			}
		}
	}

}

data class Greeting(val name: String)
