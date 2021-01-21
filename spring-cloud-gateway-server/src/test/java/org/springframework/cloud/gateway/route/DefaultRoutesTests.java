/*
 * Copyright 2013-2020 the original author or authors.
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
