package org.springframework.cloud.gateway.handler;

import org.springframework.web.server.ServerWebExchange;

import java.util.function.Predicate;

/**
 * @author Spencer Gibb
 */
public interface GatewayPredicateFactory {

	String getName();

	Predicate<ServerWebExchange> create(String value);
}
