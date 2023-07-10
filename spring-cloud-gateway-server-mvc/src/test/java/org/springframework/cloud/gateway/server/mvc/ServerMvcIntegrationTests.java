/*
 * Copyright 2013-2023 the original author or authors.
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

package org.springframework.cloud.gateway.server.mvc;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.caffeine.CaffeineProxyManager;
import io.github.bucket4j.distributed.proxy.AsyncProxyManager;
import io.github.bucket4j.distributed.remote.RemoteBucketState;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.cloud.gateway.server.mvc.common.MvcUtils;
import org.springframework.cloud.gateway.server.mvc.filter.ForwardedRequestHeadersFilter;
import org.springframework.cloud.gateway.server.mvc.filter.XForwardedRequestHeadersFilter;
import org.springframework.cloud.gateway.server.mvc.test.HttpbinTestcontainers;
import org.springframework.cloud.gateway.server.mvc.test.HttpbinUriResolver;
import org.springframework.cloud.gateway.server.mvc.test.LocalServerPortUriResolver;
import org.springframework.cloud.gateway.server.mvc.test.TestLoadBalancerConfig;
import org.springframework.cloud.gateway.server.mvc.test.client.TestRestClient;
import org.springframework.cloud.loadbalancer.annotation.LoadBalancerClient;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.cloud.gateway.server.mvc.filter.AfterFilterFunctions.addResponseHeader;
import static org.springframework.cloud.gateway.server.mvc.filter.AfterFilterFunctions.setResponseHeader;
import static org.springframework.cloud.gateway.server.mvc.filter.AfterFilterFunctions.setStatus;
import static org.springframework.cloud.gateway.server.mvc.filter.BeforeFilterFunctions.preserveHost;
import static org.springframework.cloud.gateway.server.mvc.filter.BeforeFilterFunctions.routeId;
import static org.springframework.cloud.gateway.server.mvc.filter.Bucket4jFilterFunctions.rateLimit;
import static org.springframework.cloud.gateway.server.mvc.filter.CircuitBreakerFilterFunctions.circuitBreaker;
import static org.springframework.cloud.gateway.server.mvc.filter.FilterFunctions.addRequestHeader;
import static org.springframework.cloud.gateway.server.mvc.filter.FilterFunctions.addRequestParameter;
import static org.springframework.cloud.gateway.server.mvc.filter.FilterFunctions.prefixPath;
import static org.springframework.cloud.gateway.server.mvc.filter.FilterFunctions.redirectTo;
import static org.springframework.cloud.gateway.server.mvc.filter.FilterFunctions.removeRequestHeader;
import static org.springframework.cloud.gateway.server.mvc.filter.FilterFunctions.rewritePath;
import static org.springframework.cloud.gateway.server.mvc.filter.FilterFunctions.setPath;
import static org.springframework.cloud.gateway.server.mvc.filter.FilterFunctions.setRequestHeader;
import static org.springframework.cloud.gateway.server.mvc.filter.FilterFunctions.setRequestHostHeader;
import static org.springframework.cloud.gateway.server.mvc.filter.FilterFunctions.stripPrefix;
import static org.springframework.cloud.gateway.server.mvc.filter.LoadBalancerFilterFunctions.lb;
import static org.springframework.cloud.gateway.server.mvc.filter.RetryFilterFunctions.retry;
import static org.springframework.cloud.gateway.server.mvc.handler.GatewayRouterFunctions.route;
import static org.springframework.cloud.gateway.server.mvc.handler.HandlerFunctions.http;
import static org.springframework.cloud.gateway.server.mvc.predicate.GatewayRequestPredicates.cookie;
import static org.springframework.cloud.gateway.server.mvc.predicate.GatewayRequestPredicates.header;
import static org.springframework.cloud.gateway.server.mvc.predicate.GatewayRequestPredicates.host;
import static org.springframework.cloud.gateway.server.mvc.test.TestUtils.getMap;
import static org.springframework.http.MediaType.APPLICATION_FORM_URLENCODED;
import static org.springframework.http.MediaType.MULTIPART_FORM_DATA;
import static org.springframework.web.servlet.function.RequestPredicates.GET;
import static org.springframework.web.servlet.function.RequestPredicates.POST;
import static org.springframework.web.servlet.function.RequestPredicates.path;

@SuppressWarnings("unchecked")
@SpringBootTest(properties = {}, webEnvironment = WebEnvironment.RANDOM_PORT)
@ContextConfiguration(initializers = HttpbinTestcontainers.class)
public class ServerMvcIntegrationTests {

	@LocalServerPort
	int port;

	@Autowired
	TestRestTemplate restTemplate;

	@Autowired
	TestRestClient restClient;

	@Test
	public void nonGatewayRouterFunctionWorks() {
		restClient.get().uri("/hello").exchange().expectStatus().isOk().expectBody(String.class).isEqualTo("Hello");
	}

	@SuppressWarnings("rawtypes")
	@Test
	public void addRequestHeaderWorks() {
		restClient.get().uri("/get").exchange().expectStatus().isOk().expectBody(Map.class).consumeWith(res -> {
			Map<String, Object> headers = getMap(res.getResponseBody(), "headers");
			assertThat(headers).containsEntry("X-Foo", "Bar");
		});
	}

	@Test
	public void addRequestParameterWorks() {
		restClient.get().uri("/anything/addrequestparam").exchange().expectStatus().isOk().expectBody(Map.class)
				.consumeWith(res -> {
					Map<String, Object> map = res.getResponseBody();
					Map<String, Object> args = getMap(map, "args");
					assertThat(args).containsEntry("param1", "param1val");
				});
	}

	@Test
	public void removeHopByHopRequestHeadersFilterWorks() {
		restClient.get().uri("/anything/removehopbyhoprequestheaders").exchange().expectStatus().isOk()
				.expectBody(Map.class).consumeWith(res -> {
					Map<String, Object> map = res.getResponseBody();
					Map<String, Object> headers = getMap(map, "headers");
					assertThat(headers).doesNotContainKeys("x-application-context");
				});
	}

	@Test
	public void setPathWorks() {
		restClient.get().uri("/mycustompathextra1").exchange().expectStatus().isOk().expectBody(Map.class)
				.consumeWith(res -> {
					Map<String, Object> map = res.getResponseBody();
					Map<String, Object> args = getMap(map, "args");
					assertThat(args).containsEntry("param1", "param1valextra1");
				});
	}

	@Test
	public void stripPathWorks() {
		restClient.get().uri("/long/path/to/get").exchange().expectStatus().isOk().expectBody(Map.class)
				.consumeWith(res -> {
					Map<String, Object> map = res.getResponseBody();
					Map<String, Object> headers = getMap(map, "headers");
					assertThat(headers).containsEntry("X-Test", "stripPrefix");
				});
	}

	@Test
	public void setStatusGatewayRouterFunctionWorks() {
		restClient.get().uri("/status/201").exchange().expectStatus().isEqualTo(HttpStatus.TOO_MANY_REQUESTS)
				.expectHeader().valueEquals("x-status", "201"); // .expectBody(String.class).isEqualTo("Failed
																// with 201");
	}

	@Test
	public void addResponseHeaderWorks() {
		restClient.get().uri("/anything/addresheader").exchange().expectStatus().isOk().expectBody(Map.class)
				.consumeWith(res -> {
					Map<String, Object> map = res.getResponseBody();
					Map<String, Object> headers = getMap(map, "headers");
					assertThat(headers).doesNotContainKey("x-bar");
					assertThat(res.getResponseHeaders()).containsEntry("x-bar", Collections.singletonList("val1"));
				});
	}

	@Test
	public void postWorks() {
		restClient.post().uri("/post").bodyValue("Post Value").header("test", "post").exchange().expectStatus().isOk()
				.expectBody(Map.class).consumeWith(res -> {
					Map<String, Object> map = res.getResponseBody();
					assertThat(map).isNotEmpty().containsEntry("data", "Post Value");
				});
	}

	@Test
	public void loadbalancerWorks() {
		restClient.get().uri("/anything/loadbalancer").exchange().expectStatus().isOk().expectBody(Map.class)
				.consumeWith(res -> {
					Map<String, Object> map = res.getResponseBody();
					Map<String, Object> headers = getMap(map, "headers");
					assertThat(headers).containsEntry("X-Test", "loadbalancer");
				});
	}

	@Test
	public void hostPredicateWorks() {
		String host = "www1.myjavadslhost.com";
		restClient.get().uri("/anything/hostpredicate").header("Host", host).exchange().expectStatus().isOk()
				.expectHeader().valueEquals("X-SubDomain", "www1").expectBody(Map.class).consumeWith(res -> {
					Map<String, Object> map = res.getResponseBody();
					Map<String, Object> headers = getMap(map, "headers");
					assertThat(headers).containsEntry("Host", host);
				});
	}

	@Test
	public void circuitBreakerFallbackWorks() {
		restClient.get().uri("/anything/circuitbreakerfallback").exchange().expectStatus().isOk()
				.expectBody(String.class).isEqualTo("Hello");
	}

	@Test
	public void circuitBreakerNoFallbackWorks() {
		restClient.get().uri("/anything/circuitbreakernofallback").exchange().expectStatus()
				.isEqualTo(HttpStatus.GATEWAY_TIMEOUT);
	}

	@Test
	public void retryWorks() {
		restClient.get().uri("/retry?key=get").exchange().expectStatus().isOk().expectBody(String.class).isEqualTo("3");
	}

	@Test
	public void rateLimitWorks() {
		restClient.get().uri("/anything/ratelimit").exchange().expectStatus().isOk();
		restClient.get().uri("/anything/ratelimit").exchange().expectStatus().isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
	}

	@Test
	public void headerRegexWorks() {
		restClient.get().uri("/headerregex").exchange().expectStatus().isNotFound();
		restClient.get().uri("/headerregex").header("X-MyHeader", "foo").exchange().expectStatus().isOk()
				.expectBody(Map.class).consumeWith(res -> {
					Map<String, Object> map = res.getResponseBody();
					Map<String, Object> headers = getMap(map, "headers");
					assertThat(headers).containsEntry("X-Myheader", "foo");
				});
	}

	@Test
	public void cookieRegexWorks() {
		restClient.get().uri("/cookieregex").exchange().expectStatus().isNotFound();
		restClient.get().uri("/cookieregex").cookie("mycookie", "foo").exchange().expectStatus().isOk()
				.expectBody(Map.class).consumeWith(res -> {
					Map<String, Object> map = res.getResponseBody();
					Map<String, Object> headers = getMap(map, "headers");
					assertThat(headers).containsEntry("Cookie", "mycookie=foo");
				});
	}

	@Test
	public void rewritePathWorks() {
		restClient.get().uri("/foo/get").header("Host", "www.rewritepath.org").exchange().expectStatus().isOk()
				.expectBody(Map.class).consumeWith(res -> {
					Map<String, Object> map = res.getResponseBody();
					Map<String, Object> headers = getMap(map, "headers");
					assertThat(headers).containsEntry("X-Test", "rewritepath");
				});
	}

	@Test
	public void forwardedHeadersWork() {
		restClient.get().uri("/headers").header("test", "forwarded").exchange().expectStatus().isOk()
				.expectBody(Map.class).consumeWith(res -> {
					Map<String, Object> map = res.getResponseBody();
					Map<String, Object> headers = getMap(map, "headers");
					assertThat(headers).containsKeys(ForwardedRequestHeadersFilter.FORWARDED_HEADER,
							XForwardedRequestHeadersFilter.X_FORWARDED_FOR_HEADER,
							XForwardedRequestHeadersFilter.X_FORWARDED_HOST_HEADER,
							XForwardedRequestHeadersFilter.X_FORWARDED_PORT_HEADER,
							XForwardedRequestHeadersFilter.X_FORWARDED_PROTO_HEADER);
					assertThat(headers.get(ForwardedRequestHeadersFilter.FORWARDED_HEADER)).asString()
							.contains("proto=http").contains("host=\"localhost:").contains("for=\"127.0.0.1:");
					assertThat(headers.get(XForwardedRequestHeadersFilter.X_FORWARDED_HOST_HEADER)).asString()
							.isEqualTo("localhost:" + this.port);
					assertThat(headers.get(XForwardedRequestHeadersFilter.X_FORWARDED_PORT_HEADER)).asString()
							.isEqualTo(String.valueOf(this.port));
					assertThat(headers.get(XForwardedRequestHeadersFilter.X_FORWARDED_PROTO_HEADER).toString())
							.asString().isEqualTo("http");
				});
	}

	public static final MediaType FORM_URL_ENCODED_CONTENT_TYPE = new MediaType(APPLICATION_FORM_URLENCODED,
			StandardCharsets.UTF_8);

	@Test
	void formUrlencodedWorks() {
		LinkedMultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
		formData.add("foo", "bar");
		formData.add("baz", "bam");

		// @formatter:off
		restClient.post().uri("/post").header("test", "form").contentType(FORM_URL_ENCODED_CONTENT_TYPE)
				.bodyValue(formData)
				.exchange()
				.expectStatus().isOk()
				.expectBody(Map.class).consumeWith(result -> {
					Map map = result.getResponseBody();
					Map<String, Object> form = getMap(map, "form");
					assertThat(form).containsEntry("foo", "bar");
					assertThat(form).containsEntry("baz", "bam");
				});
		// @formatter:on
	}

	@Test
	void multipartFormDataWorks() {
		MultiValueMap<String, HttpEntity<?>> formData = createMultipartData();
		// @formatter:off
		restClient.post().uri("/post").contentType(MULTIPART_FORM_DATA)
				.header("test", "form")
				.bodyValue(formData)
				.exchange()
				.expectStatus().isOk()
				.expectBody(Map.class)
				.consumeWith(result -> {
					assertMultipartData(result.getResponseBody());
				});
		// @formatter:on
	}

	@Test
	void multipartFormDataRestTemplateWorks() {
		MultiValueMap<String, HttpEntity<?>> formData = createMultipartData();
		RequestEntity<MultiValueMap<String, HttpEntity<?>>> request = RequestEntity.post("/post")
				.contentType(MULTIPART_FORM_DATA).header("test", "form").body(formData);
		ResponseEntity<Map> response = restTemplate.exchange(request, Map.class);
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertMultipartData(response.getBody());
	}

	@Test
	public void redirectToWorks() {
		restClient.get().uri("/anything/redirect").exchange().expectStatus().isEqualTo(HttpStatus.MOVED_PERMANENTLY)
				.expectHeader().valueEquals(HttpHeaders.LOCATION, "https://exampleredirect.com");
	}

	private MultiValueMap<String, HttpEntity<?>> createMultipartData() {
		ClassPathResource part = new ClassPathResource("test/1x1.png");
		MultipartBodyBuilder builder = new MultipartBodyBuilder();
		builder.part("imgpart", part, MediaType.IMAGE_PNG);
		return builder.build();
	}

	private void assertMultipartData(Map responseBody) {
		Map<String, Object> files = (Map<String, Object>) responseBody.get("files");
		assertThat(files).containsKey("imgpart");
		String file = (String) files.get("imgpart");
		assertThat(file).startsWith("data:").contains(";base64,");
	}

	@Test
	public void removeRequestHeaderWorks() {
		restClient.get().uri("/anything/removerequestheader").header("X-Request-Foo", "Bar").exchange().expectStatus()
				.isOk().expectBody(Map.class).consumeWith(res -> {
					Map<String, Object> map = res.getResponseBody();
					Map<String, Object> headers = getMap(map, "headers");
					assertThat(headers).doesNotContainKey("X-Request-Foo");
				});
	}

	@Test
	public void setRequestHeaderWorks() {
		restClient.get().uri("/headers").header("test", "setrequestheader").exchange().expectStatus().isOk()
				.expectBody(Map.class).consumeWith(res -> {
					Map<String, Object> headers = getMap(res.getResponseBody(), "headers");
					assertThat(headers).doesNotContainEntry("X-Test", "value1");
					assertThat(headers).containsEntry("X-Test", "value2");
				});
	}

	@Test
	public void setRequestHeaderHostWorks() {
		restClient.get().uri("/headers").header("Host", "www.setrequesthostheader.org").exchange().expectStatus().isOk()
				.expectBody(Map.class).consumeWith(res -> {
					Map<String, Object> headers = getMap(res.getResponseBody(), "headers");
					assertThat(headers).containsEntry("Host", "otherhost.io");
				});
	}

	@Test
	public void setResponseHeaderWorks() {
		restClient.get().uri("/anything/setresponseheader").header("test", "setresponseheader").exchange()
				.expectStatus().isOk().expectBody(Map.class).consumeWith(res -> {
					HttpHeaders headers = res.getResponseHeaders();
					assertThat(headers).doesNotContainEntry("X-Test", List.of("value1"));
					assertThat(headers).containsEntry("X-Test", List.of("value2"));
				});
	}

	@Test
	public void nestedRouteWorks() {
		testNestedRoute("nested1");
		testNestedRoute("nested2");
	}

	private void testNestedRoute(String nestedPath) {
		restClient.get().uri("/anything/nested/" + nestedPath).header("test", "nested").exchange().expectStatus().isOk()
				.expectBody(Map.class).consumeWith(res -> {
					Map<String, Object> headers = getMap(res.getResponseBody(), "headers");
					assertThat(headers).containsEntry("X-Test", nestedPath);
				});
	}

	@SpringBootConfiguration
	@EnableAutoConfiguration
	@LoadBalancerClient(name = "httpbin", configuration = TestLoadBalancerConfig.Httpbin.class)
	protected static class TestConfiguration {

		@Bean
		TestHandler testHandler() {
			return new TestHandler();
		}

		@Bean
		RetryController retryController() {
			return new RetryController();
		}

		@Bean
		public AsyncProxyManager<String> caffeineProxyManager() {
			Caffeine<String, RemoteBucketState> builder = (Caffeine) Caffeine.newBuilder().maximumSize(100);
			return new CaffeineProxyManager<>(builder, Duration.ofMinutes(1)).asAsync();
		}

		@Bean
		public RouterFunction<ServerResponse> nonGatewayRouterFunctions(TestHandler testHandler) {
			return route(GET("/hello"), testHandler::hello).withAttribute(MvcUtils.GATEWAY_ROUTE_ID_ATTR, "hello");
		}

		@Bean
		public RouterFunction<ServerResponse> gatewayRouterFunctionsAddReqHeader() {
			// @formatter:off
			return route(GET("/get"), http())
					.filter(new HttpbinUriResolver())
					.filter(addRequestHeader("X-Foo", "Bar"))
					.withAttribute(MvcUtils.GATEWAY_ROUTE_ID_ATTR, "testaddreqheader");
			// @formatter:on
		}

		@Bean
		public RouterFunction<ServerResponse> gatewayRouterFunctionsSetStatusAndAddRespHeader() {
			// @formatter:off
			return route("testsetstatus")
					.GET("/status/{status}", http())
					.before(new HttpbinUriResolver())
					.after(setStatus(HttpStatus.TOO_MANY_REQUESTS))
					.after(addResponseHeader("X-Status", "{status}"))
				.build().and(route()
					.GET("/anything/addresheader", http())
					.before(routeId("testaddresponseheader"))
					.before(new HttpbinUriResolver())
					.after(addResponseHeader("X-Bar", "val1"))
				.build());
			// @formatter:on
		}

		@Bean
		public RouterFunction<ServerResponse> gatewayRouterFunctionsAddRequestParam() {
			// @formatter:off
			return route(GET("/anything/addrequestparam"), http())
					.filter(new HttpbinUriResolver())
					.filter(addRequestParameter("param1", "param1val"))
					.withAttribute(MvcUtils.GATEWAY_ROUTE_ID_ATTR, "testaddrequestparam");
			// @formatter:on
		}

		@Bean
		public RouterFunction<ServerResponse> gatewayRouterFunctionsSetPath() {
			// @formatter:off
			return route(GET("/mycustompath{extra}"), http())
					.filter(new HttpbinUriResolver())
					.filter(setPath("/anything/mycustompath{extra}"))
					.filter(addRequestParameter("param1", "param1val{extra}"))
					.withAttribute(MvcUtils.GATEWAY_ROUTE_ID_ATTR, "testsetpath");
			// @formatter:on
		}

		@Bean
		public RouterFunction<ServerResponse> gatewayRouterFunctionsStripPrefix() {
			// @formatter:off
			return route(GET("/long/path/to/get"), http())
					.filter(new HttpbinUriResolver())
					.filter(stripPrefix(3))
					.filter(addRequestHeader("X-Test", "stripPrefix"))
					.withAttribute(MvcUtils.GATEWAY_ROUTE_ID_ATTR, "teststripprefix");
			// @formatter:on
		}

		@Bean
		public RouterFunction<ServerResponse> gatewayRouterFunctionsRemoveHopByHopRequestHeaders() {
			// @formatter:off
			return route(GET("/anything/removehopbyhoprequestheaders"), http())
					.filter(new HttpbinUriResolver())
					.filter(addRequestHeader("x-application-context", "context-id1"))
					.withAttribute(MvcUtils.GATEWAY_ROUTE_ID_ATTR, "testremovehopbyhopheaders");
			// @formatter:on
		}

		@Bean
		public RouterFunction<ServerResponse> gatewayRouterFunctionsPost() {
			// @formatter:off
			return route(POST("/post").and(header("test", "post")), http())
					.filter(new HttpbinUriResolver())
					.withAttribute(MvcUtils.GATEWAY_ROUTE_ID_ATTR, "testpost");
			// @formatter:on
		}

		@Bean
		public RouterFunction<ServerResponse> gatewayRouterFunctionsLoadBalancer() {
			// @formatter:off
			return route()
					.GET("/anything/loadbalancer", http())
					.filter(lb("httpbin"))
					.filter(addRequestHeader("X-Test", "loadbalancer"))
					.withAttribute(MvcUtils.GATEWAY_ROUTE_ID_ATTR, "testloadbalancer")
					.build();
			// @formatter:on
		}

		@Bean
		public RouterFunction<ServerResponse> gatewayRouterFunctionsHost() {
			// @formatter:off
			return route("testhostpredicate")
					.route(host("{sub}.myjavadslhost.com").and(path("/anything/hostpredicate")), http())
					.before(new HttpbinUriResolver())
					.before(preserveHost())
					.after(addResponseHeader("X-SubDomain", "{sub}"))
					.build();
			// @formatter:on
		}

		@Bean
		public RouterFunction<ServerResponse> gatewayRouterFunctionsCircuitBreakerFallback() {
			// @formatter:off
			return route(path("/anything/circuitbreakerfallback"), http(URI.create("https://nonexistantdomain.com1234")))
					.filter(circuitBreaker("mycb1", "/hello"))
					.withAttribute(MvcUtils.GATEWAY_ROUTE_ID_ATTR, "testcircuitbreakerfallback");
			// @formatter:on
		}

		@Bean
		public RouterFunction<ServerResponse> gatewayRouterFunctionsCircuitBreakerNoFallback() {
			// @formatter:off
			return route(path("/anything/circuitbreakernofallback"), http())
					.filter(new HttpbinUriResolver())
					.filter(circuitBreaker("mycb1", null))
					.filter(setPath("/delay/5"))
					.withAttribute(MvcUtils.GATEWAY_ROUTE_ID_ATTR, "testcircuitbreakernofallback");
			// @formatter:on
		}

		@Bean
		public RouterFunction<ServerResponse> gatewayRouterFunctionsRetry() {
			// @formatter:off
			return route("testretry")
					.route(path("/retry"), http())
					.before(new LocalServerPortUriResolver())
					.filter(retry(3))
					.filter(prefixPath("/do"))
					.build();
			// @formatter:on
		}

		@Bean
		public RouterFunction<ServerResponse> gatewayRouterFunctionsRateLimit() {
			// @formatter:off
			return route(GET("/anything/ratelimit"), http())
					.filter(new HttpbinUriResolver())
					//.filter(rateLimit(1, Duration.ofMinutes(1), request -> "ratelimittest1min"))
					.filter(rateLimit(c -> c.setCapacity(1)
							.setPeriod(Duration.ofMinutes(1))
							.setKeyResolver(request -> "ratelimitttest1min")))
					.withAttribute(MvcUtils.GATEWAY_ROUTE_ID_ATTR, "testratelimit");
			// @formatter:on
		}

		@Bean
		public RouterFunction<ServerResponse> gatewayRouterFunctionsHeaderPredicate() {
			// @formatter:off
			return route(path("/headerregex").and(header("X-MyHeader", "fo.")), http())
					.filter(new HttpbinUriResolver())
					.filter(setPath("/headers"))
					.withAttribute(MvcUtils.GATEWAY_ROUTE_ID_ATTR, "testheaderpredicate");
			// @formatter:on
		}

		@Bean
		public RouterFunction<ServerResponse> gatewayRouterFunctionsCookiePredicate() {
			// @formatter:off
			return route(path("/cookieregex").and(cookie("mycookie", "fo.")), http())
					.filter(new HttpbinUriResolver())
					.filter(setPath("/headers"))
					.withAttribute(MvcUtils.GATEWAY_ROUTE_ID_ATTR, "testcookiepredicate");
			// @formatter:on
		}

		@Bean
		public RouterFunction<ServerResponse> gatewayRouterFunctionsRewritePath() {
			// @formatter:off
			return route(path("/foo/**").and(host("**.rewritepath.org")), http())
					.filter(new HttpbinUriResolver())
					.filter(rewritePath("/foo/(?<segment>.*)", "/${segment}"))
					.filter(addRequestHeader("X-Test", "rewritepath"))
					.withAttribute(MvcUtils.GATEWAY_ROUTE_ID_ATTR, "testrewritepath");
			// @formatter:on
		}

		@Bean
		public RouterFunction<ServerResponse> gatewayRouterFunctionsForwardedHeaders() {
			// @formatter:off
			return route(path("/headers").and(header("test", "forwarded")), http())
					.filter(new HttpbinUriResolver())
					.filter(addRequestHeader("X-Test", "forwarded"))
					.withAttribute(MvcUtils.GATEWAY_ROUTE_ID_ATTR, "testforwardedheaders");
			// @formatter:on
		}

		@Bean
		public RouterFunction<ServerResponse> gatewayRouterFunctionsForm() {
			// @formatter:off
			return route("testform")
					.POST("/post", header("test", "form"), http())
					.before(new LocalServerPortUriResolver())
					.filter(prefixPath("/test"))
					.filter(addRequestHeader("X-Test", "form"))
					.build();
			// @formatter:on
		}

		@Bean
		public RouterFunction<ServerResponse> gatewayRouterFunctionsRedirectTo() {
			// @formatter:off
			return route(path("/anything/redirect"), http())
					.filter(redirectTo(HttpStatus.MOVED_PERMANENTLY, URI.create("https://exampleredirect.com")))
					.withAttribute(MvcUtils.GATEWAY_ROUTE_ID_ATTR, "testredirectto");
			// @formatter:on
		}

		@Bean
		public RouterFunction<ServerResponse> gatewayRouterFunctionsRemoveRequestHeader() {
			// @formatter:off
			return route(path("/anything/removerequestheader"), http())
					.filter(new HttpbinUriResolver())
					.filter(removeRequestHeader("X-Request-Foo"))
					.withAttribute(MvcUtils.GATEWAY_ROUTE_ID_ATTR, "testremoverequestheader");
			// @formatter:on
		}

		@Bean
		public RouterFunction<ServerResponse> gatewayRouterFunctionsSetRequestHeader() {
			// @formatter:off
			return route("testsetrequestheader")
					.GET("/headers", header("test", "setrequestheader"), http())
					.filter(new HttpbinUriResolver())
					.filter(addRequestHeader("X-Test", "value1"))
					.filter(setRequestHeader("X-Test", "value2"))
					.build();
			// @formatter:on
		}

		@Bean
		public RouterFunction<ServerResponse> gatewayRouterFunctionsSetRequestHostHeader() {
			// @formatter:off
			return route("testsetrequestheader")
					.route(host("**.setrequesthostheader.org"), http())
					.filter(new HttpbinUriResolver())
					.filter(setRequestHostHeader("otherhost.io"))
					.build();
			// @formatter:on
		}

		@Bean
		public RouterFunction<ServerResponse> gatewayRouterFunctionsSetResponseHeader() {
			// @formatter:off
			return route("testsetresponseheader")
					.GET("/anything/setresponseheader", header("test", "setresponseheader"), http())
					.before(new HttpbinUriResolver())
					// reverse order for "post" filters
					.after(setResponseHeader("X-Test", "value2"))
					.after(addResponseHeader("X-Test", "value1"))
					.build();
			// @formatter:on
		}

		@Bean
		public RouterFunction<ServerResponse> gatewayRouterFunctionsNested() {
			// @formatter:off
			return route()
					.nest(path("/anything/nested").and(header("test", "nested")), () ->
						route("nested1").GET("/nested1", http())
							.before(new HttpbinUriResolver())
							.filter(addRequestHeader("X-Test", "nested1"))
						.build().and(
							route("nested2").GET("/nested2", http())
							.before(new HttpbinUriResolver())
							.filter(addRequestHeader("X-Test", "nested2")).build()))
					.build();
			// @formatter:on
		}

	}

	@RestController
	protected static class RetryController {

		Log log = LogFactory.getLog(getClass());

		ConcurrentHashMap<String, AtomicInteger> map = new ConcurrentHashMap<>();

		@GetMapping("/do/retry")
		public ResponseEntity<String> retry(@RequestParam("key") String key,
				@RequestParam(name = "count", defaultValue = "3") int count,
				@RequestParam(name = "failStatus", required = false) Integer failStatus) {
			AtomicInteger num = getCount(key);
			int i = num.incrementAndGet();
			log.warn("Retry count: " + i);
			String body = String.valueOf(i);
			if (i < count) {
				HttpStatus httpStatus = HttpStatus.INTERNAL_SERVER_ERROR;
				if (failStatus != null) {
					httpStatus = HttpStatus.resolve(failStatus);
				}
				return ResponseEntity.status(httpStatus).header("X-Retry-Count", body).body("temporarily broken");
			}
			return ResponseEntity.status(HttpStatus.OK).header("X-Retry-Count", body).body(body);
		}

		AtomicInteger getCount(String key) {
			return map.computeIfAbsent(key, s -> new AtomicInteger());
		}

	}

	protected static class TestHandler {

		public ServerResponse hello(ServerRequest request) {
			return ServerResponse.ok().body("Hello");
		}

	}

}
