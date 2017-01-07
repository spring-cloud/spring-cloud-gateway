package org.springframework.cloud.gateway.config;

import org.springframework.cloud.gateway.api.RouteReader;

import java.util.List;

/**
 * @author Spencer Gibb
 */
public class PropertiesRouteReader implements RouteReader {

	private final GatewayProperties properties;

	public PropertiesRouteReader(GatewayProperties properties) {
		this.properties = properties;
	}

	@Override
	public List<Route> getRoutes() {
		return this.properties.getRoutes();
	}
}
