package org.springframework.cloud.gateway.handler.predicate;

import java.util.List;
import java.util.function.Predicate;

import org.springframework.util.Assert;
import org.springframework.web.server.ServerWebExchange;

/**
 * @author Spencer Gibb
 */
public class HeaderPredicate implements GatewayPredicate {

	@Override
	public String getName() {
		return "Header";
	}

	@Override
	public Predicate<ServerWebExchange> create(String header, String[] args) {
		//TODO: caching can happen here
		return exchange -> {
			Assert.isTrue(args != null && args.length == 1,
					"args must have one entry");

			String regexp = args[0];
			List<String> values = exchange.getRequest().getHeaders().get(header);
			for (String value : values) {
				if (value.matches(regexp)) {
					return true;
				}
			}
			return false;
		};
	}
}
