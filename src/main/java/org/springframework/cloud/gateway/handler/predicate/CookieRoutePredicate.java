package org.springframework.cloud.gateway.handler.predicate;

import java.util.List;
import java.util.function.Predicate;

import org.springframework.http.HttpCookie;
import org.springframework.web.server.ServerWebExchange;

/**
 * @author Spencer Gibb
 */
public class CookieRoutePredicate implements RoutePredicate {

	@Override
	public Predicate<ServerWebExchange> apply(String... args) {
		validate(2, args);
		String name = args[0];
		String regexp = args[1];

		return exchange -> {
			List<HttpCookie> cookies = exchange.getRequest().getCookies().get(name);
			for (HttpCookie cookie : cookies) {
				if (cookie.getValue().matches(regexp)) {
					return true;
				}
			}
			return false;
		};
	}
}
