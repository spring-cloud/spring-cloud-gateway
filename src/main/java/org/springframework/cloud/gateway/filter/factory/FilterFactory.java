package org.springframework.cloud.gateway.filter.factory;

import org.springframework.cloud.gateway.filter.GatewayFilter;

/**
 * @author Spencer Gibb
 */
public interface FilterFactory {

	String getName();

	GatewayFilter apply(String value, String[] args);
}
