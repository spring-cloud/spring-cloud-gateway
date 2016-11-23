package org.springframework.cloud.gateway;

import org.springframework.beans.BeansException;
import org.springframework.cloud.gateway.GatewayProperties.Route;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.handler.AbstractUrlHandlerMapping;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebHandler;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * @author Spencer Gibb
 */
public class GatewayUrlHandlerMapping extends AbstractUrlHandlerMapping {

	private GatewayProperties properties;
	private WebHandler webHandler;

	public GatewayUrlHandlerMapping(GatewayProperties properties, WebHandler webHandler) {
		this.properties = properties;
		this.webHandler = webHandler;
		setOrder(0);
	}

	@Override
	protected void initApplicationContext() throws BeansException {
		super.initApplicationContext();
		registerHandlers(this.properties.getRoutes());
	}

	@Override
	public Mono<Object> getHandler(ServerWebExchange exchange) {
		return super.getHandler(exchange).map(o -> {
			if (o instanceof RouteHolder) {
				RouteHolder holder = (RouteHolder) o;
				exchange.getAttributes().put(GatewayFilter.GATEWAY_ROUTE_ATTR, holder.route);
				return holder.webHandler;
			}
			return o;
		});
	}

	protected void registerHandlers(Map<String, Route> routes) {
		for (Route route : routes.values()) {
			if (StringUtils.hasText(route.getRequestPath())) {
				registerHandler(route.getRequestPath(), new RouteHolder(route, this.webHandler));
			}
		}
	}

	private class RouteHolder {
		private final Route route;
		private final WebHandler webHandler;

		public RouteHolder(Route route, WebHandler webHandler) {
			this.route = route;
			this.webHandler = webHandler;
		}
	}

}
