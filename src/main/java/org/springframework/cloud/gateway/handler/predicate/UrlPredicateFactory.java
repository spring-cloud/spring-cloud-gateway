package org.springframework.cloud.gateway.handler.predicate;

import java.util.function.Predicate;

import org.springframework.util.AntPathMatcher;
import org.springframework.util.PathMatcher;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.support.HttpRequestPathHelper;

/**
 * @author Spencer Gibb
 */
public class UrlPredicateFactory implements PredicateFactory {

	private PathMatcher pathMatcher = new AntPathMatcher();
	private HttpRequestPathHelper pathHelper = new HttpRequestPathHelper();

	public PathMatcher getPathMatcher() {
		return pathMatcher;
	}

	public void setPathMatcher(PathMatcher pathMatcher) {
		this.pathMatcher = pathMatcher;
	}

	public HttpRequestPathHelper getPathHelper() {
		return pathHelper;
	}

	public void setPathHelper(HttpRequestPathHelper pathHelper) {
		this.pathHelper = pathHelper;
	}

	@Override
	public String getName() {
		return "Url";
	}

	@Override
	public Predicate<ServerWebExchange> apply(String pattern, String[] args) {
		return exchange -> {
			String lookupPath = getPathHelper().getLookupPathForRequest(exchange);
			return getPathMatcher().match(pattern, lookupPath);
			//TODO: support trailingSlashMatch
		};
	}
}
