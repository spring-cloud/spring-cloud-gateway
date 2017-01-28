package org.springframework.cloud.gateway.api;

import org.springframework.cloud.gateway.config.Route;

import reactor.core.publisher.Flux;

/**
 * @author Spencer Gibb
 */
public class CompositeRouteReader implements RouteReader {

	private final Flux<RouteReader> delegates;

	public CompositeRouteReader(Flux<RouteReader> delegates) {
		this.delegates = delegates;
	}

	@Override
	public Flux<Route> getRoutes() {
		return this.delegates.flatMap(RouteReader::getRoutes);
	}
}
