package org.springframework.cloud.gateway.api;

import reactor.core.publisher.Flux;

/**
 * @author Spencer Gibb
 */
public interface RouteLocator {

	Flux<Route> getRoutes();
}
