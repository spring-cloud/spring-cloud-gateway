/*
 * Copyright 2018-2019 the original author or authors.
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

package org.springframework.cloud.gateway.route.builder;

import java.util.Collections;

import org.junit.Test;
import reactor.core.publisher.Mono;

import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.NettyWriteResponseFilter;
import org.springframework.cloud.gateway.filter.OrderedGatewayFilter;
import org.springframework.cloud.gateway.filter.factory.rewrite.ModifyResponseBodyGatewayFilterFactory;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.Ordered;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.server.HandlerStrategies;
import org.springframework.web.server.ServerWebExchange;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class GatewayFilterSpecTests {

	@Test
	public void orderedInterfaceRespected() {
		testFilter(MyOrderedFilter.class, new MyOrderedFilter(), 1000);
	}

	@Test
	public void unorderedWithDefaultOrder() {
		testFilter(OrderedGatewayFilter.class, new MyUnorderedFilter(), 0);
	}

	private void testFilter(Class<? extends GatewayFilter> type,
			GatewayFilter gatewayFilter, int order) {
		ConfigurableApplicationContext context = mock(
				ConfigurableApplicationContext.class);
		Route.AsyncBuilder routeBuilder = Route.async().id("123").uri("abc:123")
				.predicate(exchange -> true);
		RouteLocatorBuilder.Builder routes = new RouteLocatorBuilder(context).routes();
		GatewayFilterSpec spec = new GatewayFilterSpec(routeBuilder, routes);
		spec.filter(gatewayFilter);

		Route route = routeBuilder.build();
		assertThat(route.getFilters()).hasSize(1);
		assertFilter(route.getFilters().get(0), type, order);
	}

	private void assertFilter(GatewayFilter filter, Class<? extends GatewayFilter> type,
			int order) {
		assertThat(filter).isInstanceOf(type);
		Ordered ordered = (Ordered) filter;
		assertThat(ordered.getOrder()).isEqualTo(order);
	}

	@Test
	public void testFilters() {
		ConfigurableApplicationContext context = mock(
				ConfigurableApplicationContext.class);
		Route.AsyncBuilder routeBuilder = Route.async().id("123").uri("abc:123")
				.predicate(exchange -> true);
		RouteLocatorBuilder.Builder routes = new RouteLocatorBuilder(context).routes();
		GatewayFilterSpec spec = new GatewayFilterSpec(routeBuilder, routes);
		spec.filters(new MyUnorderedFilter(), new MyOrderedFilter());

		Route route = routeBuilder.build();
		assertThat(route.getFilters()).hasSize(2);

		assertFilter(route.getFilters().get(0), OrderedGatewayFilter.class, 0);
		assertFilter(route.getFilters().get(1), MyOrderedFilter.class, 1000);
	}

	@Test
	public void shouldSetModifyBodyResponseFilterWithRewriteFunction() {
		ConfigurableApplicationContext context = mock(
				ConfigurableApplicationContext.class);
		Route.AsyncBuilder routeBuilder = Route.async().id("123").uri("abc:123")
				.predicate(exchange -> true);

		when(context.getBean(ModifyResponseBodyGatewayFilterFactory.class))
				.thenReturn(new ModifyResponseBodyGatewayFilterFactory(
						HandlerStrategies.withDefaults().messageReaders(),
						Collections.emptySet(), Collections.emptySet()));

		RouteLocatorBuilder.Builder routes = new RouteLocatorBuilder(context).routes();
		GatewayFilterSpec spec = new GatewayFilterSpec(routeBuilder, routes);
		spec.modifyResponseBody(String.class, String.class,
				(exchange, s) -> Mono.just(s));

		Route route = routeBuilder.build();
		assertThat(route.getFilters()).hasSize(1);

		assertFilter(route.getFilters().get(0),
				ModifyResponseBodyGatewayFilterFactory.ModifyResponseGatewayFilter.class,
				NettyWriteResponseFilter.WRITE_RESPONSE_FILTER_ORDER - 1);
	}

	@Test
	public void shouldSetModifyBodyResponseFilterWithRewriteFunctionAndEmptyBodySupplier() {
		ConfigurableApplicationContext context = mock(
				ConfigurableApplicationContext.class);
		Route.AsyncBuilder routeBuilder = Route.async().id("123").uri("abc:123")
				.predicate(exchange -> true);

		when(context.getBean(ModifyResponseBodyGatewayFilterFactory.class))
				.thenReturn(new ModifyResponseBodyGatewayFilterFactory(
						HandlerStrategies.withDefaults().messageReaders(),
						Collections.emptySet(), Collections.emptySet()));

		RouteLocatorBuilder.Builder routes = new RouteLocatorBuilder(context).routes();
		GatewayFilterSpec spec = new GatewayFilterSpec(routeBuilder, routes);
		spec.modifyResponseBody(String.class, String.class,
				(exchange, s) -> Mono.just(s == null ? "emptybody" : s));

		Route route = routeBuilder.build();
		assertThat(route.getFilters()).hasSize(1);

		assertFilter(route.getFilters().get(0),
				ModifyResponseBodyGatewayFilterFactory.ModifyResponseGatewayFilter.class,
				NettyWriteResponseFilter.WRITE_RESPONSE_FILTER_ORDER - 1);
	}

	@Test
	public void shouldSetModifyBodyResponseFilterWithRewriteFunctionAndNewContentType() {
		ConfigurableApplicationContext context = mock(
				ConfigurableApplicationContext.class);
		Route.AsyncBuilder routeBuilder = Route.async().id("123").uri("abc:123")
				.predicate(exchange -> true);

		when(context.getBean(ModifyResponseBodyGatewayFilterFactory.class))
				.thenReturn(new ModifyResponseBodyGatewayFilterFactory(
						HandlerStrategies.withDefaults().messageReaders(),
						Collections.emptySet(), Collections.emptySet()));

		RouteLocatorBuilder.Builder routes = new RouteLocatorBuilder(context).routes();
		GatewayFilterSpec spec = new GatewayFilterSpec(routeBuilder, routes);
		spec.modifyResponseBody(String.class, String.class,
				MediaType.APPLICATION_JSON_VALUE, (exchange, s) -> Mono.just(s));

		Route route = routeBuilder.build();
		assertThat(route.getFilters()).hasSize(1);

		assertFilter(route.getFilters().get(0),
				ModifyResponseBodyGatewayFilterFactory.ModifyResponseGatewayFilter.class,
				NettyWriteResponseFilter.WRITE_RESPONSE_FILTER_ORDER - 1);
	}

	@Test
	public void shouldSetModifyBodyResponseFilterWithConfigConsumer() {
		ConfigurableApplicationContext context = mock(
				ConfigurableApplicationContext.class);
		Route.AsyncBuilder routeBuilder = Route.async().id("123").uri("abc:123")
				.predicate(exchange -> true);

		when(context.getBean(ModifyResponseBodyGatewayFilterFactory.class))
				.thenReturn(new ModifyResponseBodyGatewayFilterFactory(
						HandlerStrategies.withDefaults().messageReaders(),
						Collections.emptySet(), Collections.emptySet()));

		RouteLocatorBuilder.Builder routes = new RouteLocatorBuilder(context).routes();
		GatewayFilterSpec spec = new GatewayFilterSpec(routeBuilder, routes);
		spec.modifyResponseBody(
				(smth) -> new ModifyResponseBodyGatewayFilterFactory.Config()
						.setRewriteFunction(String.class, String.class,
								(exchange, s) -> Mono.just(s)));

		Route route = routeBuilder.build();
		assertThat(route.getFilters()).hasSize(1);

		assertFilter(route.getFilters().get(0),
				ModifyResponseBodyGatewayFilterFactory.ModifyResponseGatewayFilter.class,
				NettyWriteResponseFilter.WRITE_RESPONSE_FILTER_ORDER - 1);
	}

	protected static class MyOrderedFilter implements GatewayFilter, Ordered {

		@Override
		public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
			return Mono.empty();
		}

		@Override
		public int getOrder() {
			return 1000;
		}

	}

	protected static class MyUnorderedFilter implements GatewayFilter {

		@Override
		public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
			return Mono.empty();
		}

	}

}
