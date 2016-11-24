package org.springframework.cloud.gateway.filter;

import java.net.URI;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.cloud.gateway.config.GatewayProperties;
import org.springframework.cloud.gateway.config.GatewayProperties.Route;
import org.springframework.core.Ordered;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import org.springframework.web.util.UriComponentsBuilder;

import reactor.core.publisher.Mono;

/**
 * @author Spencer Gibb
 */
public class RouteToUrlFilter implements GatewayFilter, Ordered {

	private static final Log log = LogFactory.getLog(RouteToUrlFilter.class);
	public static final int ROUTE_TO_URL_FILTER_ORDER = 500;

	private final GatewayProperties properties;

	public RouteToUrlFilter(GatewayProperties properties) {
		this.properties = properties;
	}

	@Override
	public int getOrder() {
		return ROUTE_TO_URL_FILTER_ORDER;
	}

	@Override
	public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
		if (!exchange.getAttributes().containsKey(GATEWAY_ROUTE_ATTR)) {
			return chain.filter(exchange);
		}
		log.info("RouteToUrlFilter start");
		Object gatewayRoute = exchange.getAttributes().get(GATEWAY_ROUTE_ATTR);
		if (!(gatewayRoute instanceof Route)) {
			return Mono.error(new IllegalStateException(GATEWAY_ROUTE_ATTR +
					" not an instance of " + Route.class.getSimpleName() +
					", is " + gatewayRoute.getClass()));
		}
		Route route = (Route) gatewayRoute;
		URI requestUrl = UriComponentsBuilder.fromHttpRequest(exchange.getRequest())
				.uri(route.getDownstreamUrl())
				.build(true)
				.toUri();
		exchange.getAttributes().put(GATEWAY_REQUEST_URL_ATTR, requestUrl);
		return chain.filter(exchange);
	}

}
