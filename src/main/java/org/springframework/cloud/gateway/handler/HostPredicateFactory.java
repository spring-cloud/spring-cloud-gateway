package org.springframework.cloud.gateway.handler;

import java.util.function.Predicate;

import org.springframework.util.AntPathMatcher;
import org.springframework.util.PathMatcher;
import org.springframework.web.server.ServerWebExchange;

/**
 * @author Spencer Gibb
 */
public class HostPredicateFactory implements GatewayPredicateFactory {

	private PathMatcher pathMatcher = new AntPathMatcher(".");

	public void setPathMatcher(PathMatcher pathMatcher) {
		this.pathMatcher = pathMatcher;
	}

	@Override
	public String getName() {
		return "Host";
	}

	@Override
	public Predicate<ServerWebExchange> create(String pattern) {
		//TODO: caching can happen here
		return exchange -> {
			String host = exchange.getRequest().getHeaders().getFirst("Host");
			return this.pathMatcher.match(pattern, host);
		};
	}
}
