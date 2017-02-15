package org.springframework.cloud.gateway.config;

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.gateway.api.FilterDefinition;
import org.springframework.cloud.gateway.api.Route;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;

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
	private List<FilterDefinition> defaultFilters = new ArrayList<>();

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
