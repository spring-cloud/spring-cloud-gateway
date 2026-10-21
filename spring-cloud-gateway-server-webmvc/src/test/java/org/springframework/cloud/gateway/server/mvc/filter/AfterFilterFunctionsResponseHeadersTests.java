/*
 * Copyright 2026-present the original author or authors.
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

package org.springframework.cloud.gateway.server.mvc.filter;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.caffeine.CaffeineProxyManager;
import io.github.bucket4j.distributed.proxy.AsyncProxyManager;
import io.github.bucket4j.distributed.remote.RemoteBucketState;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.cloud.client.circuitbreaker.CircuitBreaker;
import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;
import org.springframework.cloud.function.context.FunctionCatalog;
import org.springframework.cloud.function.context.config.ContextFunctionCatalogAutoConfiguration;
import org.springframework.cloud.gateway.server.mvc.handler.GatewayServerResponse;
import org.springframework.cloud.stream.function.StreamOperations;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.core.task.TaskExecutor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.converter.ByteArrayHttpMessageConverter;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.DispatcherServlet;
import org.springframework.web.servlet.function.AsyncServerResponse;
import org.springframework.web.servlet.function.EntityResponse;
import org.springframework.web.servlet.function.HandlerFunction;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.RouterFunctions;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.springframework.boot.autoconfigure.task.TaskExecutionAutoConfiguration.APPLICATION_TASK_EXECUTOR_BEAN_NAME;
import static org.springframework.cloud.gateway.server.mvc.filter.AfterFilterFunctions.addResponseHeader;
import static org.springframework.cloud.gateway.server.mvc.filter.AfterFilterFunctions.dedupeResponseHeader;
import static org.springframework.cloud.gateway.server.mvc.filter.AfterFilterFunctions.removeResponseHeader;
import static org.springframework.cloud.gateway.server.mvc.filter.AfterFilterFunctions.rewriteLocationResponseHeader;
import static org.springframework.cloud.gateway.server.mvc.filter.AfterFilterFunctions.rewriteResponseHeader;
import static org.springframework.cloud.gateway.server.mvc.filter.AfterFilterFunctions.setResponseHeader;
import static org.springframework.cloud.gateway.server.mvc.filter.Bucket4jFilterFunctions.rateLimit;
import static org.springframework.cloud.gateway.server.mvc.filter.CircuitBreakerFilterFunctions.circuitBreaker;
import static org.springframework.cloud.gateway.server.mvc.filter.FilterFunctions.redirectTo;
import static org.springframework.cloud.gateway.server.mvc.handler.GatewayRouterFunctions.route;
import static org.springframework.cloud.gateway.server.mvc.handler.HandlerFunctions.fn;
import static org.springframework.cloud.gateway.server.mvc.handler.HandlerFunctions.stream;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.web.servlet.function.RequestPredicates.path;

class AfterFilterFunctionsResponseHeadersTests {

	@Test
	void addResponseHeaderOnCustomHandlerRoute() throws Exception {
		RouterFunction<ServerResponse> fooRoute = route("fooRoute")
			.GET(path("/foo"), req -> ServerResponse.ok().build())
			.after(addResponseHeader("X-Test", "TestFoo"))
			.build();

		MockMvcBuilders.routerFunctions(fooRoute)
			.build()
			.perform(get("/foo"))
			.andExpect(status().isOk())
			.andExpect(header().string("X-Test", "TestFoo"))
			.andExpect(content().string(""));
	}

	@Test
	void responseHeaderFiltersPreserveEntityBody() throws Exception {
		RouterFunction<ServerResponse> fooRoute = route("fooRoute")
			.GET("/foo",
					req -> ServerResponse.ok()
						.header("X-Source", "old")
						.header("X-Remove", "old")
						.header("X-Duplicate", "same")
						.header("X-Rewrite", "old")
						.body("hello"))
			.after(dedupeResponseHeader("X-Duplicate"))
			.after(addResponseHeader("X-Duplicate", "same"))
			.after(rewriteResponseHeader("X-Rewrite", "old", "new"))
			.after(setResponseHeader("X-Source", "new"))
			.after(removeResponseHeader("X-Remove"))
			.build();

		MockMvcBuilders.routerFunctions(fooRoute)
			.build()
			.perform(get("/foo"))
			.andExpect(status().isOk())
			.andExpect(header().stringValues("X-Source", "new"))
			.andExpect(header().doesNotExist("X-Remove"))
			.andExpect(header().stringValues("X-Duplicate", "same"))
			.andExpect(header().string("X-Rewrite", "new"))
			.andExpect(content().string("hello"));
	}

	@Test
	void locationResponseHeaderFilterPreservesBody() throws Exception {
		RouterFunction<ServerResponse> fooRoute = route("fooRoute")
			.GET("/foo", req -> ServerResponse.ok().header("Location", "https://upstream.example/foo").body("hello"))
			.after(rewriteLocationResponseHeader())
			.build();

		MockMvcBuilders.routerFunctions(fooRoute)
			.build()
			.perform(get("/foo").header("Host", "gateway.example"))
			.andExpect(status().isOk())
			.andExpect(header().string("Location", "https://gateway.example/foo"))
			.andExpect(content().string("hello"));
	}

	@Test
	void customFilterCanMutateDownstreamResponse() throws Exception {
		RouterFunction<ServerResponse> fooRoute = route("fooRoute")
			.GET("/foo", req -> ServerResponse.ok().body("hello"))
			.filter((request, next) -> {
				ServerResponse response = next.handle(request);
				response.headers().add("X-Custom", "yes");
				return response;
			})
			.build();

		MockMvcBuilders.routerFunctions(fooRoute)
			.build()
			.perform(get("/foo"))
			.andExpect(status().isOk())
			.andExpect(header().string("X-Custom", "yes"))
			.andExpect(content().string("hello"));
	}

	@Test
	void customFilterShortCircuitResponseIsAdapted() throws Exception {
		RouterFunction<ServerResponse> fooRoute = route("fooRoute").GET("/foo", req -> {
			throw new AssertionError("Handler must not be called");
		})
			.after(addResponseHeader("X-Test", "TestFoo"))
			.filter((request, next) -> ServerResponse.status(HttpStatus.FORBIDDEN).body("denied"))
			.build();

		MockMvcBuilders.routerFunctions(fooRoute)
			.build()
			.perform(get("/foo"))
			.andExpect(status().isForbidden())
			.andExpect(header().string("X-Test", "TestFoo"))
			.andExpect(content().string("denied"));
	}

	@Test
	void responseReplacedByAfterFilterIsAdapted() throws Exception {
		RouterFunction<ServerResponse> fooRoute = route("fooRoute")
			.GET("/foo", req -> ServerResponse.ok().body("original"))
			.after(addResponseHeader("X-Test", "TestFoo"))
			.after((request, response) -> ServerResponse.accepted().body("replacement"))
			.build();

		MockMvcBuilders.routerFunctions(fooRoute)
			.build()
			.perform(get("/foo"))
			.andExpect(status().isAccepted())
			.andExpect(header().string("X-Test", "TestFoo"))
			.andExpect(content().string("replacement"));
	}

	@Test
	void responseFiltersKeepTheirExecutionOrder() throws Exception {
		RouterFunction<ServerResponse> fooRoute = route("fooRoute").GET("/foo", req -> ServerResponse.ok().build())
			.after(addResponseHeader("X-Order", "outer"))
			.after(addResponseHeader("X-Order", "inner"))
			.build();

		MockMvcBuilders.routerFunctions(fooRoute)
			.build()
			.perform(get("/foo"))
			.andExpect(header().stringValues("X-Order", "inner", "outer"));
	}

	@Test
	void pathNestedResponseFiltersAreAdapted() throws Exception {
		RouterFunction<ServerResponse> fooRoute = route("fooRoute")
			.path("/api",
					nested -> nested.GET("/foo", req -> ServerResponse.ok().body("hello"))
						.after(addResponseHeader("X-Test", "TestFoo")))
			.build();

		MockMvcBuilders.routerFunctions(fooRoute)
			.build()
			.perform(get("/api/foo"))
			.andExpect(status().isOk())
			.andExpect(header().string("X-Test", "TestFoo"))
			.andExpect(content().string("hello"));
	}

	@Test
	void predicateNestedResponseFiltersAreAdapted() throws Exception {
		RouterFunction<ServerResponse> fooRoute = route("fooRoute")
			.nest(path("/api"),
					nested -> nested.GET("/foo", req -> ServerResponse.ok().body("hello"))
						.after(addResponseHeader("X-Test", "TestFoo")))
			.build();

		MockMvcBuilders.routerFunctions(fooRoute)
			.build()
			.perform(get("/api/foo"))
			.andExpect(status().isOk())
			.andExpect(header().string("X-Test", "TestFoo"))
			.andExpect(content().string("hello"));
	}

	@Test
	void resourceFluentResponseFiltersAreAdapted() throws Exception {
		RouterFunction<ServerResponse> fooRoute = route("fooRoute")
			.resource(path("/foo"), new ByteArrayResource("hello".getBytes(StandardCharsets.UTF_8)))
			.after(addResponseHeader("X-Test", "TestFoo"))
			.build();

		MockMvcBuilders.routerFunctions(fooRoute)
			.build()
			.perform(get("/foo"))
			.andExpect(status().isOk())
			.andExpect(header().string("X-Test", "TestFoo"))
			.andExpect(content().string("hello"));
	}

	@Test
	void typedEntityResponseProcessorStillReceivesEntityResponse() throws Exception {
		RouterFunction<ServerResponse> fooRoute = route("fooRoute")
			.GET("/foo", req -> EntityResponse.fromObject("hello").build())
			.after((ServerRequest request, EntityResponse<String> response) -> {
				assertThat(response.entity()).isEqualTo("hello");
				response.headers().add("X-Typed", "yes");
				return response;
			})
			.build();

		MockMvcBuilders.routerFunctions(fooRoute)
			.build()
			.perform(get("/foo"))
			.andExpect(header().string("X-Typed", "yes"))
			.andExpect(content().string("hello"));
	}

	@Test
	void responseHeaderFilterPreservesCustomBodyWriter() throws Exception {
		RouterFunction<ServerResponse> fooRoute = route("fooRoute")
			.GET("/foo", req -> ServerResponse.ok().header("X-Source", "old").build((request, response) -> {
				response.getWriter().write("custom");
				response.flushBuffer();
				return null;
			}))
			.after(setResponseHeader("X-Source", "new"))
			.build();

		MockMvcBuilders.routerFunctions(fooRoute)
			.build()
			.perform(get("/foo"))
			.andExpect(status().isOk())
			.andExpect(header().stringValues("X-Source", "new"))
			.andExpect(content().string("custom"));
	}

	@Test
	void typedAsyncResponseFilterStillReceivesAsyncServerResponse() throws Exception {
		ServerResponse completed = ServerResponse.ok().body("hello");
		RouterFunction<ServerResponse> fooRoute = route("fooRoute")
			.GET("/foo", req -> ServerResponse.async(CompletableFuture.completedFuture(completed)))
			.filter((ServerRequest request, HandlerFunction<AsyncServerResponse> next) -> {
				AsyncServerResponse response = next.handle(request);
				assertThat(response.block()).isSameAs(completed);
				return response;
			})
			.build();

		MockMvcBuilders.routerFunctions(fooRoute)
			.build()
			.perform(get("/foo"))
			.andExpect(status().isOk())
			.andExpect(content().string("hello"));
	}

	@Test
	void outerErrorHandlerStillHandlesNestedResponseWriteFailure() throws Exception {
		RouterFunction<ServerResponse> fooRoute = route("fooRoute")
			.path("/api", nested -> nested.GET("/foo", req -> ServerResponse.ok().build((request, response) -> {
				throw new IOException("write failed");
			})).filter((request, next) -> next.handle(request)))
			.onError(IOException.class,
					(failure, request) -> ServerResponse.status(HttpStatus.BAD_GATEWAY).body("fallback"))
			.build();

		MockMvcBuilders.routerFunctions(fooRoute)
			.build()
			.perform(get("/api/foo"))
			.andExpect(status().isBadGateway())
			.andExpect(content().string("fallback"));
	}

	@Test
	void nestedResponseHeaderMutationRetainsOuterWriteErrorHandling() throws Exception {
		RouterFunction<ServerResponse> fooRoute = route("fooRoute")
			.path("/api", nested -> nested.GET("/foo", req -> ServerResponse.ok().build((request, response) -> {
				throw new IOException("write failed");
			})).after(addResponseHeader("X-Test", "TestFoo")))
			.onError(IOException.class,
					(failure, request) -> ServerResponse.status(HttpStatus.BAD_GATEWAY)
						.header("X-Fallback", "yes")
						.body("fallback"))
			.build();

		MockMvcBuilders.routerFunctions(fooRoute)
			.build()
			.perform(get("/api/foo"))
			.andExpect(status().isBadGateway())
			.andExpect(header().string("X-Test", "TestFoo"))
			.andExpect(header().string("X-Fallback", "yes"))
			.andExpect(content().string("fallback"));
	}

	@Test
	void firstRegisteredErrorHandlerKeepsPrecedenceForHandlerAndWriteFailures() throws Exception {
		RouterFunction<ServerResponse> fooRoute = route("fooRoute").GET("/handler", req -> {
			throw new IOException("handler failed");
		}).path("/api", nested -> nested.GET("/writer", req -> ServerResponse.ok().build((request, response) -> {
			throw new IOException("write failed");
		})).filter((request, next) -> next.handle(request)))
			.onError(IOException.class,
					(failure, request) -> ServerResponse.status(HttpStatus.BAD_GATEWAY).body("first"))
			.onError(IOException.class,
					(failure, request) -> ServerResponse.status(HttpStatus.SERVICE_UNAVAILABLE).body("second"))
			.after(addResponseHeader("X-Test", "TestFoo"))
			.build();
		MockMvc mockMvc = MockMvcBuilders.routerFunctions(fooRoute).build();

		for (String path : List.of("/handler", "/api/writer")) {
			mockMvc.perform(get(path))
				.andExpect(status().isBadGateway())
				.andExpect(header().string("X-Test", "TestFoo"))
				.andExpect(content().string("first"));
		}
	}

	@Test
	void requestProcessorsKeepTheirOrderRelativeToFilters() throws Exception {
		List<String> calls = new ArrayList<>();
		RouterFunction<ServerResponse> fooRoute = route("fooRoute").GET("/foo", req -> {
			calls.add("handler");
			return ServerResponse.ok().build();
		}).before(request -> {
			calls.add("beforeOuter");
			return request;
		}).filter((request, next) -> {
			calls.add("filterBefore");
			ServerResponse response = next.handle(request);
			calls.add("filterAfter");
			return response;
		}).before(request -> {
			calls.add("beforeInner");
			return request;
		}).build();

		MockMvcBuilders.routerFunctions(fooRoute).build().perform(get("/foo")).andExpect(status().isOk());

		assertThat(calls).containsExactly("beforeOuter", "filterBefore", "beforeInner", "handler", "filterAfter");
	}

	@Test
	void buildingAgainDoesNotChangePreviouslyBuiltFilterChains() throws Exception {
		RouterFunctions.Builder builder = route("fooRoute").GET("/foo", req -> ServerResponse.ok().build())
			.after(addResponseHeader("X-Test", "first"));
		RouterFunction<ServerResponse> firstRoute = builder.build();
		RouterFunction<ServerResponse> secondRoute = builder.after(addResponseHeader("X-Test", "second")).build();

		MockMvcBuilders.routerFunctions(firstRoute)
			.build()
			.perform(get("/foo"))
			.andExpect(header().stringValues("X-Test", "first"));
		MockMvcBuilders.routerFunctions(secondRoute)
			.build()
			.perform(get("/foo"))
			.andExpect(header().stringValues("X-Test", "second", "first"));
	}

	@Test
	void outerErrorHandlerStillHandlesNestedAsyncResponseFailure() throws Exception {
		CompletableFuture<ServerResponse> response = new CompletableFuture<>();
		RouterFunction<ServerResponse> fooRoute = route("fooRoute")
			.path("/api",
					nested -> nested.GET("/foo", req -> ServerResponse.async(response))
						.filter((request, next) -> next.handle(request)))
			.onError(IOException.class,
					(failure, request) -> ServerResponse.status(HttpStatus.BAD_GATEWAY).body("fallback"))
			.build();
		MockMvc mockMvc = MockMvcBuilders.routerFunctions(fooRoute).build();

		MvcResult result = mockMvc.perform(get("/api/foo"))
			.andExpect(MockMvcResultMatchers.request().asyncStarted())
			.andReturn();
		response.completeExceptionally(new IOException("async failed"));
		result.getAsyncResult(5000);
		mockMvc.perform(asyncDispatch(result))
			.andExpect(status().isBadGateway())
			.andExpect(content().string("fallback"));
	}

	@Test
	void filterDoesNotReadUnresolvedAsyncResponseMetadata() throws Exception {
		CompletableFuture<ServerResponse> response = new CompletableFuture<>();
		RouterFunction<ServerResponse> fooRoute = route("fooRoute").GET("/foo", req -> ServerResponse.async(response))
			.filter((request, next) -> next.handle(request))
			.build();
		MockMvc mockMvc = MockMvcBuilders.routerFunctions(fooRoute).build();

		MvcResult result = mockMvc.perform(get("/foo"))
			.andExpect(MockMvcResultMatchers.request().asyncStarted())
			.andReturn();
		response.complete(ServerResponse.ok().body("hello"));
		result.getAsyncResult(5000);
		mockMvc.perform(asyncDispatch(result)).andExpect(status().isOk()).andExpect(content().string("hello"));
	}

	@Test
	void responseHeaderFilterPreservesPublisherBody() throws Exception {
		RouterFunction<ServerResponse> fooRoute = route("fooRoute")
			.GET("/foo", req -> ServerResponse.ok().body(Flux.just("hello", "world")))
			.after(addResponseHeader("X-Test", "TestFoo"))
			.build();
		MockMvc mockMvc = MockMvcBuilders.routerFunctions(fooRoute).build();

		MvcResult result = mockMvc.perform(get("/foo"))
			.andExpect(MockMvcResultMatchers.request().asyncStarted())
			.andReturn();
		result.getAsyncResult(5000);
		mockMvc.perform(asyncDispatch(result))
			.andExpect(status().isOk())
			.andExpect(header().string("X-Test", "TestFoo"))
			.andExpect(content().string("helloworld"));
	}

	@Test
	void responseHeaderFilterPreservesCompletionStageBody() throws Exception {
		CompletableFuture<String> body = new CompletableFuture<>();
		RouterFunction<ServerResponse> fooRoute = route("fooRoute").GET("/foo", req -> ServerResponse.ok().body(body))
			.after(addResponseHeader("X-Test", "TestFoo"))
			.build();
		MockMvc mockMvc = MockMvcBuilders.routerFunctions(fooRoute).build();

		MvcResult result = mockMvc.perform(get("/foo"))
			.andExpect(MockMvcResultMatchers.request().asyncStarted())
			.andReturn();
		body.complete("hello");
		result.getAsyncResult(5000);
		mockMvc.perform(asyncDispatch(result))
			.andExpect(status().isOk())
			.andExpect(header().string("X-Test", "TestFoo"))
			.andExpect(content().string("hello"));
	}

	@Test
	void addResponseHeaderOnServerResponseBuild() {
		assertThatThrownBy(() -> apply(addResponseHeader("X-Test", "TestFoo"), ServerResponse.ok().build()))
			.isInstanceOf(UnsupportedOperationException.class);
	}

	@Test
	void addResponseHeaderOnGatewayServerResponseBuild() {
		ServerResponse response = apply(addResponseHeader("X-Test", "TestFoo"), GatewayServerResponse.ok().build());

		assertThat(response.headers().getFirst("X-Test")).isEqualTo("TestFoo");
	}

	@Test
	void addResponseHeaderAfterRedirectToFilter() throws Exception {
		RouterFunction<ServerResponse> redirectRoute = route("redirectRoute")
			.GET(path("/redirect"), req -> ServerResponse.ok().build())
			.after(addResponseHeader("X-Test", "TestRedirect"))
			.filter(redirectTo(HttpStatus.FOUND, URI.create("https://example.org")))
			.build();

		ServerResponse response = handle(redirectRoute, request("GET", "/redirect", null));

		assertThat(response.statusCode()).isEqualTo(HttpStatus.FOUND);
		assertThat(response.headers().getFirst("X-Test")).isEqualTo("TestRedirect");
	}

	@Test
	void addResponseHeaderAfterFnHandler() {
		new WebApplicationContextRunner()
			.withConfiguration(AutoConfigurations.of(ContextFunctionCatalogAutoConfiguration.class))
			.withUserConfiguration(UpperFunctionConfiguration.class)
			.run(context -> {
				RouterFunction<ServerResponse> fnRoute = route("fnRoute").POST(path("/fn"), fn("upper"))
					.after(addResponseHeader("X-Test", "TestFn"))
					.build();
				ServerRequest request = request("POST", "/fn", context, "hello");

				ServerResponse response = handle(fnRoute, request);

				assertThat(response.statusCode()).isEqualTo(HttpStatus.OK);
				assertThat(response.headers().getFirst("X-Test")).isEqualTo("TestFn");
			});
	}

	@Test
	void addResponseHeaderAfterFnHandlerPreservesPublisherBody() {
		new WebApplicationContextRunner()
			.withConfiguration(AutoConfigurations.of(ContextFunctionCatalogAutoConfiguration.class))
			.withUserConfiguration(UpperFunctionConfiguration.class)
			.run(context -> {
				RouterFunction<ServerResponse> fnRoute = route("fnRoute").POST(path("/fn"), fn("upperFlux"))
					.after(addResponseHeader("X-Test", "TestFn"))
					.build();
				ServerRequest request = request("POST", "/fn", context, "hello");

				ServerResponse response = fnRoute.route(request).orElseThrow().handle(request);
				MockHttpServletResponse servletResponse = write(response, request);

				assertThat(servletResponse.getStatus()).isEqualTo(HttpStatus.OK.value());
				assertThat(servletResponse.getHeader("X-Test")).isEqualTo("TestFn");
				assertThat(servletResponse.getContentAsString()).isEqualTo("HELLO");
			});
	}

	@Test
	void addResponseHeaderAfterFnHandlerFunctionNotFound() {
		new WebApplicationContextRunner().withBean(FunctionCatalog.class, () -> mock(FunctionCatalog.class))
			.run(context -> {
				RouterFunction<ServerResponse> fnRoute = route("fnRoute").GET(path("/fn"), fn("missing"))
					.after(addResponseHeader("X-Test", "TestFn"))
					.build();

				ServerResponse response = handle(fnRoute, request("GET", "/fn", context));

				assertThat(response.statusCode()).isEqualTo(HttpStatus.NOT_FOUND);
				assertThat(response.headers().getFirst("X-Test")).isEqualTo("TestFn");
			});
	}

	@Test
	void addResponseHeaderAfterFnHandlerConsumerPost() {
		consumerContextRunner().run(context -> {
			RouterFunction<ServerResponse> fnRoute = route("fnRoute").POST(path("/fn"), fn("sink"))
				.after(addResponseHeader("X-Test", "TestFn"))
				.build();

			ServerResponse response = handle(fnRoute, request("POST", "/fn", context, "hello"));

			assertThat(response.statusCode()).isEqualTo(HttpStatus.ACCEPTED);
			assertThat(response.headers().getFirst("X-Test")).isEqualTo("TestFn");
		});
	}

	@Test
	void addResponseHeaderAfterFnHandlerConsumerDelete() {
		consumerContextRunner().run(context -> {
			RouterFunction<ServerResponse> fnRoute = route("fnRoute").DELETE(path("/fn"), fn("sink"))
				.after(addResponseHeader("X-Test", "TestFn"))
				.build();

			ServerResponse response = handle(fnRoute, request("DELETE", "/fn", context, "hello"));

			assertThat(response.statusCode()).isEqualTo(HttpStatus.OK);
			assertThat(response.headers().getFirst("X-Test")).isEqualTo("TestFn");
		});
	}

	@Test
	void addResponseHeaderAfterStreamHandlerSendFailed() {
		StreamOperations streamOperations = mock(StreamOperations.class);
		given(streamOperations.send(anyString(), any())).willReturn(false);
		new WebApplicationContextRunner().withBean(StreamOperations.class, () -> streamOperations).run(context -> {
			RouterFunction<ServerResponse> streamRoute = route("streamRoute").POST(path("/stream"), stream("binding"))
				.after(addResponseHeader("X-Test", "TestStream"))
				.build();

			ServerResponse response = handle(streamRoute, request("POST", "/stream", context, "hello"));

			assertThat(response.statusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
			assertThat(response.headers().getFirst("X-Test")).isEqualTo("TestStream");
		});
	}

	@Test
	void rateLimitAllowedAddsHeaderToStreamHandlerResponse() {
		StreamOperations streamOperations = mock(StreamOperations.class);
		given(streamOperations.send(anyString(), any())).willReturn(true);
		rateLimitContextRunner().withBean(StreamOperations.class, () -> streamOperations).run(context -> {
			RouterFunction<ServerResponse> rateLimitRoute = route("rateLimitRoute")
				.POST(path("/limited"), stream("binding"))
				.filter(rateLimit(1, Duration.ofMinutes(1), req -> "client"))
				.build();

			ServerResponse response = handle(rateLimitRoute, request("POST", "/limited", context, "hello"));

			assertThat(response.statusCode()).isEqualTo(HttpStatus.ACCEPTED);
			assertThat(response.headers().getFirst(Bucket4jFilterFunctions.DEFAULT_HEADER_NAME)).isEqualTo("0");
		});
	}

	@Test
	void addResponseHeaderAfterStreamHandler() {
		StreamOperations streamOperations = mock(StreamOperations.class);
		given(streamOperations.send(anyString(), any())).willReturn(true);
		new WebApplicationContextRunner().withBean(StreamOperations.class, () -> streamOperations).run(context -> {
			RouterFunction<ServerResponse> streamRoute = route("streamRoute").POST(path("/stream"), stream("binding"))
				.after(addResponseHeader("X-Test", "TestStream"))
				.build();
			ServerRequest request = request("POST", "/stream", context, "hello");

			ServerResponse response = handle(streamRoute, request);

			assertThat(response.statusCode()).isEqualTo(HttpStatus.ACCEPTED);
			assertThat(response.headers().getFirst("X-Test")).isEqualTo("TestStream");
		});
	}

	@Test
	void addResponseHeaderAfterRateLimitRejectsEmptyKey() {
		rateLimitContextRunner().run(context -> {
			RouterFunction<ServerResponse> rateLimitRoute = route("rateLimitRoute")
				.GET(path("/limited"), req -> ServerResponse.ok().build())
				.after(addResponseHeader("X-Test", "TestRateLimit"))
				.filter(rateLimit(1, Duration.ofMinutes(1), req -> ""))
				.build();

			ServerResponse response = handle(rateLimitRoute, request("GET", "/limited", context));

			assertThat(response.statusCode()).isEqualTo(HttpStatus.FORBIDDEN);
			assertThat(response.headers().getFirst("X-Test")).isEqualTo("TestRateLimit");
		});
	}

	@Test
	void addResponseHeaderAfterRateLimitExceeded() {
		rateLimitContextRunner().run(context -> {
			RouterFunction<ServerResponse> rateLimitRoute = route("rateLimitRoute")
				.GET(path("/limited"), req -> ServerResponse.ok().build())
				.after(addResponseHeader("X-Test", "TestRateLimit"))
				.filter(rateLimit(1, Duration.ofMinutes(1), req -> "client"))
				.build();
			handle(rateLimitRoute, request("GET", "/limited", context));

			ServerResponse response = handle(rateLimitRoute, request("GET", "/limited", context));

			assertThat(response.statusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
			assertThat(response.headers().getFirst("X-Test")).isEqualTo("TestRateLimit");
		});
	}

	@Test
	void addResponseHeaderAfterCircuitBreakerResumeWithoutError() {
		CircuitBreakerFactory<?, ?> circuitBreakerFactory = mock(CircuitBreakerFactory.class);
		given(circuitBreakerFactory.create("cb")).willReturn(new FallbackOnErrorCircuitBreaker());
		new WebApplicationContextRunner().withBean(CircuitBreakerFactory.class, () -> circuitBreakerFactory)
			.run(context -> {
				RouterFunction<ServerResponse> circuitBreakerRoute = route("circuitBreakerRoute")
					.GET(path("/cb"), req -> {
						throw new IllegalStateException("boom");
					})
					.after(addResponseHeader("X-Test", "TestCircuitBreaker"))
					.filter(circuitBreaker(config -> config.setId("cb").setResumeWithoutError(true)))
					.build();

				ServerResponse response = handle(circuitBreakerRoute, request("GET", "/cb", context));

				assertThat(response.statusCode()).isEqualTo(HttpStatus.OK);
				assertThat(response.headers().getFirst("X-Test")).isEqualTo("TestCircuitBreaker");
			});
	}

	private static WebApplicationContextRunner consumerContextRunner() {
		return new WebApplicationContextRunner()
			.withConfiguration(AutoConfigurations.of(ContextFunctionCatalogAutoConfiguration.class))
			.withUserConfiguration(SinkConsumerConfiguration.class);
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	private static WebApplicationContextRunner rateLimitContextRunner() {
		Caffeine<String, RemoteBucketState> caffeine = (Caffeine) Caffeine.newBuilder().maximumSize(100);
		AsyncProxyManager<String> proxyManager = new CaffeineProxyManager<>(caffeine, Duration.ofMinutes(1)).asAsync();
		return new WebApplicationContextRunner().withBean(AsyncProxyManager.class, () -> proxyManager)
			.withBean(APPLICATION_TASK_EXECUTOR_BEAN_NAME, TaskExecutor.class, SyncTaskExecutor::new);
	}

	private static ServerResponse handle(RouterFunction<ServerResponse> routerFunction, ServerRequest request)
			throws Exception {
		ServerResponse response = routerFunction.route(request).orElseThrow().handle(request);
		MockHttpServletResponse servletResponse = write(response, request);
		assertThat(servletResponse.getStatus()).isEqualTo(response.statusCode().value());
		response.headers()
			.forEach((name, values) -> assertThat(servletResponse.getHeaders(name)).containsExactlyElementsOf(values));
		return response;
	}

	private static MockHttpServletResponse write(ServerResponse response, ServerRequest request) throws Exception {
		MockHttpServletResponse servletResponse = new MockHttpServletResponse();
		response.writeTo(request.servletRequest(), servletResponse, request::messageConverters);
		return servletResponse;
	}

	private static ServerResponse apply(BiFunction<ServerRequest, ServerResponse, ServerResponse> filter,
			ServerResponse response) {
		return filter.apply(request("GET", "/foo", null), response);
	}

	private static ServerRequest request(String method, String path, @Nullable WebApplicationContext context) {
		return request(method, path, context, null);
	}

	private static ServerRequest request(String method, String path, @Nullable WebApplicationContext context,
			@Nullable String body) {
		MockHttpServletRequest servletRequest = new MockHttpServletRequest(method, path);
		servletRequest.setAsyncSupported(true);
		if (context != null) {
			servletRequest.setAttribute(DispatcherServlet.WEB_APPLICATION_CONTEXT_ATTRIBUTE, context);
		}
		if (body != null) {
			servletRequest.setContentType(MediaType.TEXT_PLAIN_VALUE);
			servletRequest.setContent(body.getBytes(StandardCharsets.UTF_8));
		}
		return ServerRequest.create(servletRequest,
				List.of(new StringHttpMessageConverter(), new ByteArrayHttpMessageConverter()));
	}

	@Configuration(proxyBeanMethods = false)
	static class UpperFunctionConfiguration {

		@Bean
		Function<String, String> upper() {
			return String::toUpperCase;
		}

		@Bean
		Function<String, Flux<String>> upperFlux() {
			return value -> Flux.just(value.toUpperCase(Locale.ROOT));
		}

	}

	@Configuration(proxyBeanMethods = false)
	static class SinkConsumerConfiguration {

		@Bean
		Consumer<String> sink() {
			return value -> {
			};
		}

	}

	static class FallbackOnErrorCircuitBreaker implements CircuitBreaker {

		@Override
		public <T> T run(Supplier<T> toRun, Function<@Nullable Throwable, T> fallback) {
			try {
				return toRun.get();
			}
			catch (RuntimeException ex) {
				return fallback.apply(ex);
			}
		}

	}

}
