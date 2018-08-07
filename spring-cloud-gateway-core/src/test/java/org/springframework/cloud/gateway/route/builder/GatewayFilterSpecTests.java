package org.springframework.cloud.gateway.route.builder;

import org.junit.Test;
import org.springframework.cloud.gateway.filter.OrderedGatewayFilter;
import reactor.core.publisher.Mono;

import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.Ordered;
import org.springframework.web.server.ServerWebExchange;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

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
		ConfigurableApplicationContext context = mock(ConfigurableApplicationContext.class);
		Route.AsyncBuilder routeBuilder = Route.async()
				.id("123")
				.uri("abc:123")
				.predicate(exchange -> true);
		RouteLocatorBuilder.Builder routes = new RouteLocatorBuilder(context).routes();
		GatewayFilterSpec spec = new GatewayFilterSpec(routeBuilder, routes);
		spec.filter(gatewayFilter);

		Route route = routeBuilder.build();
		assertThat(route.getFilters()).hasSize(1);
		assertFilter(route.getFilters().get(0), type, order);
	}

	private void assertFilter(GatewayFilter filter, Class<? extends GatewayFilter> type, int order) {
		assertThat(filter).isInstanceOf(type);
		Ordered ordered = (Ordered) filter;
		assertThat(ordered.getOrder()).isEqualTo(order);
	}

	@Test
	public void testFilters() {
		ConfigurableApplicationContext context = mock(ConfigurableApplicationContext.class);
		Route.AsyncBuilder routeBuilder = Route.async()
				.id("123")
				.uri("abc:123")
				.predicate(exchange -> true);
		RouteLocatorBuilder.Builder routes = new RouteLocatorBuilder(context).routes();
		GatewayFilterSpec spec = new GatewayFilterSpec(routeBuilder, routes);
		spec.filters(new MyUnorderedFilter(), new MyOrderedFilter());

		Route route = routeBuilder.build();
		assertThat(route.getFilters()).hasSize(2);

		assertFilter(route.getFilters().get(0), OrderedGatewayFilter.class, 0);
		assertFilter(route.getFilters().get(1), MyOrderedFilter.class, 1000);
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
