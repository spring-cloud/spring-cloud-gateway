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

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
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
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.core.task.TaskExecutor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.converter.ByteArrayHttpMessageConverter;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.DispatcherServlet;
import org.springframework.web.servlet.function.RouterFunction;
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
import static org.springframework.cloud.gateway.server.mvc.filter.Bucket4jFilterFunctions.rateLimit;
import static org.springframework.cloud.gateway.server.mvc.filter.CircuitBreakerFilterFunctions.circuitBreaker;
import static org.springframework.cloud.gateway.server.mvc.filter.FilterFunctions.redirectTo;
import static org.springframework.cloud.gateway.server.mvc.handler.GatewayRouterFunctions.route;
import static org.springframework.cloud.gateway.server.mvc.handler.HandlerFunctions.fn;
import static org.springframework.cloud.gateway.server.mvc.handler.HandlerFunctions.stream;
import static org.springframework.web.servlet.function.RequestPredicates.path;

class AfterFilterFunctionsResponseHeadersTests {

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
			.GET(path("/redirect"), req -> GatewayServerResponse.ok().build())
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
				.GET(path("/limited"), req -> GatewayServerResponse.ok().build())
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
				.GET(path("/limited"), req -> GatewayServerResponse.ok().build())
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
		return routerFunction.route(request).orElseThrow().handle(request);
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
