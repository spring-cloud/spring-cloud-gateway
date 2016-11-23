package org.springframework.cloud.gateway.filters;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.cloud.gateway.GatewayProperties;
import org.springframework.cloud.gateway.GatewayProperties.Route;
import org.springframework.cloud.gateway.GatewayApplication;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;

import java.net.URI;

import static org.springframework.util.StringUtils.hasText;

/**
 * @author Spencer Gibb
 */
public class FindRouteFilter implements WebFilter, Ordered {

	private static final Log log = LogFactory.getLog(GatewayApplication.class);

	private final GatewayProperties properties;
	private final AntPathMatcher pathMatcher = new AntPathMatcher();
	private final AntPathMatcher hostMatcher = new AntPathMatcher(".");

	public FindRouteFilter(GatewayProperties properties) {
		this.properties = properties;
	}

	@Override
	public int getOrder() {
		return 500;
	}

	@Override
	public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
		log.info("FindRouteFilter start");
		//TODO:
		ServerHttpRequest request = exchange.getRequest();
		URI uri = request.getURI();
		String path = uri.getPath();
		String host = uri.getHost();
		for (Route route : this.properties.getRoutes().values()) {
			if (hasText(route.getRequestPath())
					&& this.pathMatcher.match(route.getRequestPath(), path)) {
				populateRequestUrl(exchange, request, route);
			// TODO: this stuff needs to move into GatewayHandlerMapping
			// otherwise, only path based routing works
			} else if (hasText(route.getRequestHost())
					&& this.hostMatcher.match(route.getRequestHost(), host)) {
				populateRequestUrl(exchange, request, route);
			}
		}
		return chain.filter(exchange);
	}

	private void populateRequestUrl(ServerWebExchange exchange, ServerHttpRequest request, Route route) {
		URI requestUrl = UriComponentsBuilder.fromHttpRequest(request)
				.uri(route.getDownstreamUrl())
				.build(true)
				.toUri();
		exchange.getAttributes().put("requestUrl", requestUrl);
	}
}
