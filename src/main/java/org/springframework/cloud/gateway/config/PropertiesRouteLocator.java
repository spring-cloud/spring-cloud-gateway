package org.springframework.cloud.gateway.config;

import org.springframework.cloud.gateway.api.Route;
import org.springframework.cloud.gateway.api.RouteLocator;

import reactor.core.publisher.Flux;

/**
 * @author Spencer Gibb
 */
public class PropertiesRouteLocator implements RouteLocator {

	private final GatewayProperties properties;

	public PropertiesRouteLocator(GatewayProperties properties) {
		this.properties = properties;
	}

	@Override
	public Flux<Route> getRoutes() {
		return Flux.fromIterable(this.properties.getRoutes());
	}
}
