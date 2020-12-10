package org.springframework.cloud.gateway.support;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.cloud.gateway.config.GatewayProperties;
import org.springframework.cloud.gateway.event.FilterArgsEvent;
import org.springframework.cloud.gateway.filter.FilterDefinition;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.OrderedGatewayFilter;
import org.springframework.cloud.gateway.filter.factory.GatewayFilterFactory;
import org.springframework.core.Ordered;

/**
 * @author chentong
 */
public class GatewayFilterContext {

	private static final Logger logger = LoggerFactory
			.getLogger(GatewayFilterContext.class);

	public static final String DEFAULT_FILTERS = "defaultFilters";

	private final GatewayProperties gatewayProperties;

	private final ConfigurationService configurationService;

	private final Map<String, GatewayFilterFactory> gatewayFilterFactories = new HashMap<>();

	public GatewayFilterContext(GatewayProperties gatewayProperties,
			ConfigurationService configurationService,
			List<GatewayFilterFactory> gatewayFilterFactories) {
		this.gatewayProperties = gatewayProperties;
		this.configurationService = configurationService;
		gatewayFilterFactories.forEach(
				factory -> this.gatewayFilterFactories.put(factory.name(), factory));
	}

	public List<GatewayFilter> getDefaultGatewayFilters() {
		if (gatewayProperties.getDefaultFilters().isEmpty()) {
			return new ArrayList<>();
		}

		return loadGatewayFilters(DEFAULT_FILTERS,
				new ArrayList<>(gatewayProperties.getDefaultFilters()));
	}

	public List<GatewayFilter> loadGatewayFilters(String id,
			List<FilterDefinition> filterDefinitions) {
		ArrayList<GatewayFilter> ordered = new ArrayList<>(filterDefinitions.size());
		for (int i = 0; i < filterDefinitions.size(); i++) {
			FilterDefinition definition = filterDefinitions.get(i);
			GatewayFilterFactory factory = this.gatewayFilterFactories
					.get(definition.getName());
			if (factory == null) {
				throw new IllegalArgumentException(
						"Unable to find GatewayFilterFactory with name "
								+ definition.getName());
			}
			if (logger.isDebugEnabled()) {
				logger.debug("RouteDefinition " + id + " applying filter "
						+ definition.getArgs() + " to " + definition.getName());
			}

			// @formatter:off
			Object configuration = this.configurationService.with(factory)
					.name(definition.getName())
					.properties(definition.getArgs())
					.eventFunction((bound, properties) -> new FilterArgsEvent(
							// TODO: why explicit cast needed or java compile fails
							GatewayFilterContext.this, id, (Map<String, Object>) properties))
					.bind();
			// @formatter:on

			if (configuration instanceof HasRouteId) {
				HasRouteId hasRouteId = (HasRouteId) configuration;
				hasRouteId.setRouteId(id);
			}

			GatewayFilter gatewayFilter = factory.apply(configuration);
			if (gatewayFilter instanceof Ordered) {
				ordered.add(gatewayFilter);
			}
			else {
				ordered.add(new OrderedGatewayFilter(gatewayFilter, i + 1));
			}
		}

		return ordered;
	}

}
