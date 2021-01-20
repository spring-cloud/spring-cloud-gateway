package org.springframework.cloud.gateway.route;

import java.util.Arrays;
import java.util.List;

import org.junit.Test;

import org.springframework.cloud.gateway.config.GatewayProperties;
import org.springframework.cloud.gateway.filter.FilterDefinition;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AddResponseHeaderGatewayFilterFactory;
import org.springframework.cloud.gateway.filter.factory.GatewayFilterFactory;
import org.springframework.cloud.gateway.filter.factory.RemoveResponseHeaderGatewayFilterFactory;
import org.springframework.cloud.gateway.support.ConfigurationService;

import static org.assertj.core.api.Assertions.assertThat;

public class DefaultRoutesTests {

	@Test
	public void testGetDefaultGatewayFilters() {
		List<GatewayFilterFactory> gatewayFilterFactories = Arrays.asList(
				new RemoveResponseHeaderGatewayFilterFactory(),
				new AddResponseHeaderGatewayFilterFactory(),
				new RouteDefinitionRouteLocatorTests.TestOrderedGatewayFilterFactory());
		GatewayProperties gatewayProperties = new GatewayProperties();
		gatewayProperties.setDefaultFilters(
				Arrays.asList(new FilterDefinition("RemoveResponseHeader=Server"),
						new FilterDefinition("AddResponseHeader=X-Response-Foo, Bar")));

		@SuppressWarnings("deprecation")
		DefaultRoutes defaultRoutes = new DefaultRoutes(gatewayProperties,
				gatewayFilterFactories, new ConfigurationService());

		List<GatewayFilter> defaultGatewayFilters = defaultRoutes
				.getDefaultGatewayFilters();
		assertThat(defaultGatewayFilters.size()).isEqualTo(2);
	}

}
