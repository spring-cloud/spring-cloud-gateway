package org.springframework.cloud.gateway.filters;

import java.net.URI;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.cloud.gateway.GatewayFilter;
import org.springframework.cloud.gateway.GatewayProperties;
import org.springframework.cloud.gateway.GatewayProperties.Route;
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

	private final GatewayProperties properties;

	public RouteToUrlFilter(GatewayProperties properties) {
		this.properties = properties;
	}

	@Override
	public int getOrder() {
		// TODO: move to constant
		return 500;
	}

	// TODO: do we really need shouldFilter or just move the if into filter?
	@Override
	public boolean shouldFilter(ServerWebExchange exchange) {
		//TODO: move to constant
		return exchange.getAttributes().containsKey("gatewayRoute");
	}

	@Override
	public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
		log.info("RouteToUrlFilter start");
		Object gatewayRoute = exchange.getAttributes().get("gatewayRoute");
		if (!(gatewayRoute instanceof Route)) {
			return Mono.error(new IllegalStateException("gatewayRoute" +
					" not an instance of " + Route.class.getSimpleName() +
					", is " + gatewayRoute.getClass()));
		}
		Route route = (Route) gatewayRoute;
		URI requestUrl = UriComponentsBuilder.fromHttpRequest(exchange.getRequest())
				.uri(route.getDownstreamUrl())
				.build(true)
				.toUri();
		//TODO: move to constant
		exchange.getAttributes().put("requestUrl", requestUrl);
		return chain.filter(exchange);
	}

}
