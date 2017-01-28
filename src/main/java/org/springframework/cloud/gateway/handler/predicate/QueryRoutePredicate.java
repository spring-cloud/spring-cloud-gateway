package org.springframework.cloud.gateway.handler.predicate;

import java.util.List;
import java.util.function.Predicate;

import org.springframework.web.server.ServerWebExchange;

/**
 * @author Spencer Gibb
 */
public class QueryRoutePredicate implements RoutePredicate {

	@Override
	public Predicate<ServerWebExchange> apply(String... args) {
		validate(1, args);
		String param = args[0];

		return exchange -> {
			if (args.length < 2) {
				// check existence of header
				return exchange.getRequest().getQueryParams().containsKey(param);
			}

			String regexp = args[1];

			List<String> values = exchange.getRequest().getQueryParams().get(param);
			for (String value : values) {
				if (value.matches(regexp)) {
					return true;
				}
			}
			return false;
		};
	}
}
