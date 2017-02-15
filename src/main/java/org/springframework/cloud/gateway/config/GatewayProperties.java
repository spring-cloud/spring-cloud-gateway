package org.springframework.cloud.gateway.config;

import java.util.ArrayList;
import java.util.List;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.gateway.api.FilterDefinition;
import org.springframework.cloud.gateway.api.Route;
import org.springframework.cloud.gateway.filter.route.RemoveNonProxyHeadersRouteFilter;

import static org.springframework.cloud.gateway.support.NameUtils.normalizeFilterName;

/**
 * @author Spencer Gibb
 */
@ConfigurationProperties("spring.cloud.gateway")
public class GatewayProperties {

	/**
	 * List of Routes
	 */
	@NotNull
	@Valid
	private List<Route> routes = new ArrayList<>();

	/**
	 * List of filter definitions that are applied to every route.
	 */
	private List<FilterDefinition> defaultFilters = loadDefaults();

	private ArrayList<FilterDefinition> loadDefaults() {
		ArrayList<FilterDefinition> defaults = new ArrayList<>();
		FilterDefinition definition = new FilterDefinition();
		definition.setName(normalizeFilterName(RemoveNonProxyHeadersRouteFilter.class));
		defaults.add(definition);
		return defaults;
	}

	public List<Route> getRoutes() {
		return routes;
	}

	public void setRoutes(List<Route> routes) {
		this.routes = routes;
	}

	public List<FilterDefinition> getDefaultFilters() {
		return defaultFilters;
	}

	public void setDefaultFilters(List<FilterDefinition> defaultFilters) {
		this.defaultFilters = defaultFilters;
	}
}
