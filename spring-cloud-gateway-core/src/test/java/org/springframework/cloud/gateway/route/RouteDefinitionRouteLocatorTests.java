package org.springframework.cloud.gateway.route;

import java.net.URI;
import java.util.Arrays;
import java.util.List;

import org.junit.Test;
import org.springframework.cloud.gateway.config.GatewayProperties;
import org.springframework.cloud.gateway.config.PropertiesRouteDefinitionLocator;
import org.springframework.cloud.gateway.filter.FilterDefinition;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.OrderedGatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.cloud.gateway.filter.factory.AddResponseHeaderGatewayFilterFactory;
import org.springframework.cloud.gateway.filter.factory.GatewayFilterFactory;
import org.springframework.cloud.gateway.filter.factory.RemoveResponseHeaderGatewayFilterFactory;
import org.springframework.cloud.gateway.handler.predicate.HostRoutePredicateFactory;
import org.springframework.cloud.gateway.handler.predicate.PredicateDefinition;
import org.springframework.cloud.gateway.handler.predicate.RoutePredicateFactory;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Toshiaki Maki
 */
public class RouteDefinitionRouteLocatorTests {

	@Test
	public void contextLoads() {
		List<RoutePredicateFactory> predicates = Arrays
				.asList(new HostRoutePredicateFactory());
		List<GatewayFilterFactory> gatewayFilterFactories = Arrays.asList(
				new RemoveResponseHeaderGatewayFilterFactory(),
				new AddResponseHeaderGatewayFilterFactory(),
				new TestOrderedGatewayFilterFactory());
		GatewayProperties gatewayProperties = new GatewayProperties();
		gatewayProperties.setRoutes(Arrays.asList(new RouteDefinition() {
			{
				setId("foo");
				setUri(URI.create("https://foo.example.com"));
				setPredicates(
						Arrays.asList(new PredicateDefinition("Host=*.example.com")));
				setFilters(Arrays.asList(
						new FilterDefinition("RemoveResponseHeader=Server"),
						new FilterDefinition("TestOrdered="),
						new FilterDefinition("AddResponseHeader=X-Response-Foo, Bar")));
			}
		}));

		RouteDefinitionRouteLocator routeDefinitionRouteLocator = new RouteDefinitionRouteLocator(
				new PropertiesRouteDefinitionLocator(gatewayProperties), predicates,
				gatewayFilterFactories, gatewayProperties);

		List<Route> routes = routeDefinitionRouteLocator.getRoutes().collectList()
				.block();
		List<GatewayFilter> filters = routes.get(0).getFilters();
		assertThat(filters).hasSize(3);
		assertThat(getFilterClassName(filters.get(0))).startsWith("RemoveResponseHeader");
		assertThat(getFilterClassName(filters.get(1))).startsWith("AddResponseHeader");
		assertThat(getFilterClassName(filters.get(2)))
				.startsWith("RouteDefinitionRouteLocatorTests$TestOrderedGateway");
	}

	private String getFilterClassName(GatewayFilter target) {
		if (target instanceof OrderedGatewayFilter) {
			return getFilterClassName(((OrderedGatewayFilter) target).getDelegate());
		}
		else {
			return target.getClass().getSimpleName();
		}
	}

	static class TestOrderedGatewayFilterFactory extends AbstractGatewayFilterFactory {
		@Override
		public GatewayFilter apply(Object config) {
			return new OrderedGatewayFilter((exchange, chain) -> chain.filter(exchange),
					9999);
		}
	}
}
