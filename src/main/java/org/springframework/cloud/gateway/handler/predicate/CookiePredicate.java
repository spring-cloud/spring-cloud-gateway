package org.springframework.cloud.gateway.handler.predicate;

import java.util.List;
import java.util.function.Predicate;

import org.springframework.http.HttpCookie;
import org.springframework.util.Assert;
import org.springframework.web.server.ServerWebExchange;

/**
 * @author Spencer Gibb
 */
public class CookiePredicate implements GatewayPredicate {

	@Override
	public String getName() {
		return "Cookie";
	}

	@Override
	public Predicate<ServerWebExchange> apply(String name, String[] args) {
		//TODO: caching can happen here
		return exchange -> {
			Assert.isTrue(args != null && args.length == 1,
					"args must have one entry");

			String regexp = args[0];
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
