package org.springframework.cloud.gateway.filters;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.cloud.gateway.GatewayProperties;
import org.springframework.cloud.gateway.GatewayProperties.Route;
import org.springframework.cloud.gateway.SpringCloudGatewayApplication;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.net.URI;

/**
 * @author Spencer Gibb
 */
public class FindRouteFilter implements WebFilter {

	private static final Log log = LogFactory.getLog(SpringCloudGatewayApplication.class);

	private final GatewayProperties properties;
	private final AntPathMatcher matcher;

	public FindRouteFilter(GatewayProperties properties) {
		this.properties = properties;
		this.matcher = new AntPathMatcher();
	}

	@Override
	public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
		log.info("FindRouteFilter start");
		//TODO:
		ServerHttpRequest request = exchange.getRequest();
		URI uri = request.getURI();
		String path = uri.getPath();
		for (Route route : this.properties.getRoutes().values()) {
			if (this.matcher.match(route.getPath(), path)) {
				String url = route.getUrl() + path;
				exchange.getAttributes().put("requestUrl", url);
				return chain.filter(exchange);
			}
		}
		return chain.filter(exchange);
	}
}
