package org.springframework.cloud.gateway.handler.predicate;

import java.util.Map;
import java.util.function.Predicate;

import org.springframework.util.AntPathMatcher;
import org.springframework.util.PathMatcher;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.support.HttpRequestPathHelper;

/**
 * @author Spencer Gibb
 */
public class UrlRoutePredicate implements RoutePredicate {

	public static final String URL_PREDICATE_VARS_ATTR = "urlPredicateVars";

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
	public Predicate<ServerWebExchange> apply(String... args) {
		validate(1, args);
		String pattern = args[0];

		return exchange -> {
			String lookupPath = getPathHelper().getLookupPathForRequest(exchange);
			boolean match = getPathMatcher().match(pattern, lookupPath);
			if (match) {
				Map<String, String> variables = getPathMatcher().extractUriTemplateVariables(pattern, lookupPath);
				exchange.getAttributes().put(URL_PREDICATE_VARS_ATTR, variables);
			}
			return match;
			//TODO: support trailingSlashMatch
		};
	}
}
