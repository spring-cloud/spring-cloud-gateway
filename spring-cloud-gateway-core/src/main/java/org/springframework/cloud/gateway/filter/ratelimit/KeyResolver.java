package org.springframework.cloud.gateway.filter.ratelimit;

import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * @author Spencer Gibb
 */
//TODO: KeyResolver for exchange.getPrincipal().flatMap(principal -> {})
public interface KeyResolver {
	Mono<String> resolve(ServerWebExchange exchange);
}
