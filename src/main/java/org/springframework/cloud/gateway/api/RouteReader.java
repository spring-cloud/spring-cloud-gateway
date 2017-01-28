package org.springframework.cloud.gateway.api;

import org.springframework.cloud.gateway.config.Route;

import reactor.core.publisher.Flux;

/**
 * @author Spencer Gibb
 */
public interface RouteReader {

	Flux<Route> getRoutes();
}
