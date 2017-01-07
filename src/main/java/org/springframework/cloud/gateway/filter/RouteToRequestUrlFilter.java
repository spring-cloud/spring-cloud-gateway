package org.springframework.cloud.gateway.filter;

import java.net.URI;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.cloud.gateway.config.Route;
import org.springframework.core.Ordered;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import org.springframework.web.util.UriComponentsBuilder;

import reactor.core.publisher.Mono;

/**
 * @author Spencer Gibb
 */
public class RouteToRequestUrlFilter implements GatewayFilter, Ordered {

	private static final Log log = LogFactory.getLog(RouteToRequestUrlFilter.class);
	public static final int ROUTE_TO_URL_FILTER_ORDER = 500;

	@Override
	public int getOrder() {
		return ROUTE_TO_URL_FILTER_ORDER;
	}

	@Override
	public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
		Route route = getAttribute(exchange, GATEWAY_ROUTE_ATTR, Route.class);
		if (route == null) {
			return chain.filter(exchange);
		}
		log.info("RouteToRequestUrlFilter start");
		URI requestUrl = UriComponentsBuilder.fromHttpRequest(exchange.getRequest())
				.uri(route.getUri())
				.build(true)
				.toUri();
		exchange.getAttributes().put(GATEWAY_REQUEST_URL_ATTR, requestUrl);
		return chain.filter(exchange);
	}

}
