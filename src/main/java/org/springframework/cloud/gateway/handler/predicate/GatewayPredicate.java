package org.springframework.cloud.gateway.handler.predicate;

import java.util.function.Predicate;

import org.springframework.web.server.ServerWebExchange;

/**
 * @author Spencer Gibb
 */
public interface GatewayPredicate {

	String getName();

	Predicate<ServerWebExchange> apply(String value, String[] args);
}
