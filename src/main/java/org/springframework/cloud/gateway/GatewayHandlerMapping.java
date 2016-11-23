package org.springframework.cloud.gateway;

import org.springframework.beans.BeansException;
import org.springframework.cloud.gateway.GatewayProperties.Route;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.handler.AbstractUrlHandlerMapping;
import org.springframework.web.server.ServerWebExchange;

import java.util.Map;

/**
 * @author Spencer Gibb
 */
public class GatewayHandlerMapping extends AbstractUrlHandlerMapping {

	private GatewayProperties properties;
	private GatewayWebHandler gatewayWebHandler;

	public GatewayHandlerMapping(GatewayProperties properties, GatewayWebHandler gatewayWebHandler) {
		this.properties = properties;
		this.gatewayWebHandler = gatewayWebHandler;
	}

	@Override
	protected void initApplicationContext() throws BeansException {
		super.initApplicationContext();
		registerHandlers(this.properties.getRoutes());
	}

	protected void registerHandlers(Map<String, Route> routes) {
		for (Route route : routes.values()) {
			if (StringUtils.hasText(route.getRequestPath())) {
				registerHandler(route.getRequestPath(), this.gatewayWebHandler);
			}
		}
	}

}
