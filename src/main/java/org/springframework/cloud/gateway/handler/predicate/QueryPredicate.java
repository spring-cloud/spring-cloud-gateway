package org.springframework.cloud.gateway.handler.predicate;

import java.util.List;
import java.util.function.Predicate;

import org.springframework.web.server.ServerWebExchange;

/**
 * @author Spencer Gibb
 */
public class QueryPredicate implements GatewayPredicate {

	@Override
	public String getName() {
		return "Query";
	}

	@Override
	public Predicate<ServerWebExchange> apply(String param, String[] args) {
		//TODO: caching can happen here
		return exchange -> {
			String regexp = null;

			if (args != null && args.length == 1) {
				regexp = args[0];
			}

			if (regexp == null) {
				// check existence of header
				return exchange.getRequest().getQueryParams().containsKey(param);
			}

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
