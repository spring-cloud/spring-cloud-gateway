package org.springframework.cloud.gateway.handler.predicate;

import java.util.function.Predicate;

import org.springframework.util.AntPathMatcher;
import org.springframework.util.PathMatcher;
import org.springframework.web.server.ServerWebExchange;

/**
 * @author Spencer Gibb
 */
public class HostRoutePredicate implements RoutePredicate {

	private PathMatcher pathMatcher = new AntPathMatcher(".");

	public void setPathMatcher(PathMatcher pathMatcher) {
		this.pathMatcher = pathMatcher;
	}

	@Override
	public Predicate<ServerWebExchange> apply(String[] args) {
		validate(1, args);
		String pattern = args[0];

		return exchange -> {
			String host = exchange.getRequest().getHeaders().getFirst("Host");
			return this.pathMatcher.match(pattern, host);
		};
	}
}
