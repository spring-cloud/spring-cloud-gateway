package org.springframework.cloud.gateway.handler.predicate;

import java.util.List;
import java.util.function.Predicate;

import org.springframework.web.server.ServerWebExchange;

/**
 * @author Spencer Gibb
 */
public class HeaderRoutePredicate implements RoutePredicate {

	@Override
	public Predicate<ServerWebExchange> apply(String... args) {
		validate(2, args);
		String header = args[0];
		String regexp = args[1];

		return exchange -> {

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
