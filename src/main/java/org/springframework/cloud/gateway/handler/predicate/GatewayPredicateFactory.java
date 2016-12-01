package org.springframework.cloud.gateway.handler.predicate;

import java.util.function.Predicate;

import org.springframework.web.server.ServerWebExchange;

/**
 * @author Spencer Gibb
 */
public interface GatewayPredicateFactory {

	String getName();

	Predicate<ServerWebExchange> create(String value, String[] args);
}
