package org.springframework.cloud.gateway.support;

import java.net.URI;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;
import org.junit.function.ThrowingRunnable;

import org.springframework.cloud.gateway.config.GatewayProperties;
import org.springframework.cloud.gateway.filter.FilterDefinition;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AddResponseHeaderGatewayFilterFactory;
import org.springframework.cloud.gateway.filter.factory.GatewayFilterFactory;
import org.springframework.cloud.gateway.filter.factory.RemoveResponseHeaderGatewayFilterFactory;
import org.springframework.cloud.gateway.handler.predicate.PredicateDefinition;
import org.springframework.cloud.gateway.route.RouteDefinition;

import static org.assertj.core.api.Assertions.assertThat;

public class GatewayFilterContextTests {

	@Test
	public void getDefaultGatewayFiltersEmpty() {
		GatewayFilterContext gatewayFilterContext = new GatewayFilterContext(
				new GatewayProperties(), new ConfigurationService(),
				Collections.emptyList());
		List<GatewayFilter> filters = gatewayFilterContext.getDefaultGatewayFilters();

		assertThat(filters).isEmpty();
	}

	@Test
	public void getDefaultGatewayFiltersSuccess() {
		List<GatewayFilterFactory> gatewayFilterFactories = Arrays.asList(
				new RemoveResponseHeaderGatewayFilterFactory(),
				new AddResponseHeaderGatewayFilterFactory());

		GatewayProperties gatewayProperties = new GatewayProperties();
		gatewayProperties.setDefaultFilters(defaultFilterDefinitions());

		GatewayFilterContext gatewayFilterContext = new GatewayFilterContext(
				gatewayProperties, new ConfigurationService(), gatewayFilterFactories);

		List<GatewayFilter> filters = gatewayFilterContext.getDefaultGatewayFilters();
		assertThat(filters.size()).isEqualTo(2);
	}

	@Test
	public void getDefaultGatewayFiltersThrowException() {
		List<GatewayFilterFactory> gatewayFilterFactories = Arrays
				.asList(new RemoveResponseHeaderGatewayFilterFactory());

		GatewayProperties gatewayProperties = new GatewayProperties();
		gatewayProperties.setDefaultFilters(defaultFilterDefinitions());

		GatewayFilterContext gatewayFilterContext = new GatewayFilterContext(
				gatewayProperties, new ConfigurationService(), gatewayFilterFactories);

		Assert.assertThrows(
				"Unable to find GatewayFilterFactory with name AddResponseHeader",
				IllegalArgumentException.class, new ThrowingRunnable() {
					@Override
					public void run() throws Throwable {
						gatewayFilterContext.getDefaultGatewayFilters();
					}
				});
	}

	private List<FilterDefinition> defaultFilterDefinitions() {
		FilterDefinition removeResponseHeaderDefinition = new FilterDefinition();
		removeResponseHeaderDefinition.setName("RemoveResponseHeader");

		FilterDefinition addResponseHeaderDefinition = new FilterDefinition();
		addResponseHeaderDefinition.setName("AddResponseHeader");

		return Arrays.asList(removeResponseHeaderDefinition, addResponseHeaderDefinition);

	}

}
