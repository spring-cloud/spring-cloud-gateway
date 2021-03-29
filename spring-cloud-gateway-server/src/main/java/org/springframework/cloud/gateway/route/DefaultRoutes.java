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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.springframework.cloud.gateway.config.GatewayProperties;
import org.springframework.cloud.gateway.filter.FilterDefinition;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.GatewayFilterFactory;
import org.springframework.cloud.gateway.support.ConfigurationService;

/**
 * @author chentong
 */
public class DefaultRoutes extends AbstractRouteLocator {

	/**
	 * Default filters name.
	 */
	public static final String DEFAULT_FILTERS = "defaultFilters";

	private final List<GatewayFilter> defaultGatewayFilters = new ArrayList<>();

	private final GatewayProperties gatewayProperties;

	public DefaultRoutes(GatewayProperties gatewayProperties, List<GatewayFilterFactory> gatewayFilterFactories,
			ConfigurationService configurationService) {
		super(gatewayFilterFactories, configurationService);
		this.gatewayProperties = gatewayProperties;
	}

	public List<GatewayFilter> getDefaultGatewayFilters() {
		if (defaultGatewayFilters.isEmpty()) {
			List<FilterDefinition> defaultFilterDefinitions = gatewayProperties.getDefaultFilters();
			if (defaultFilterDefinitions.isEmpty()) {
				return new ArrayList<>();
			}

			defaultGatewayFilters.addAll(loadGatewayFilters(DEFAULT_FILTERS, defaultFilterDefinitions));

		}
		return Collections.unmodifiableList(defaultGatewayFilters);
	}

}
