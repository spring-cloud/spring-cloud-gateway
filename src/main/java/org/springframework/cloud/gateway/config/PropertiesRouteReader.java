package org.springframework.cloud.gateway.config;

import org.springframework.cloud.gateway.api.RouteReader;

import reactor.core.publisher.Flux;

/**
 * @author Spencer Gibb
 */
public class PropertiesRouteReader implements RouteReader {

	private final GatewayProperties properties;

	public PropertiesRouteReader(GatewayProperties properties) {
		this.properties = properties;
	}

	@Override
	public Flux<Route> getRoutes() {
		return Flux.fromIterable(this.properties.getRoutes());
	}
}
