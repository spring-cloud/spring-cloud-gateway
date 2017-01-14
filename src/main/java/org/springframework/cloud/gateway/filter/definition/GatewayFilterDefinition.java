package org.springframework.cloud.gateway.filter.definition;

import org.springframework.cloud.gateway.filter.GatewayFilter;

/**
 * @author Spencer Gibb
 */
public interface GatewayFilterDefinition {

	String getName();

	GatewayFilter apply(String value, String[] args);
}
