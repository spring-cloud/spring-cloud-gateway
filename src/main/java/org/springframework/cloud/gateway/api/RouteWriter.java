package org.springframework.cloud.gateway.api;

import reactor.core.publisher.Mono;

/**
 * @author Spencer Gibb
 */
public interface RouteWriter {

	Mono<Void> save(Mono<Route> route);

	Mono<Void> delete(Mono<String> routeId);
}
