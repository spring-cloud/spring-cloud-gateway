package org.springframework.cloud.gateway.filter.route;

import org.springframework.web.server.WebFilter;

/**
 * @author Spencer Gibb
 */
public class SetResponseHeaderRouteFilter implements RouteFilter {

	@Override
	public WebFilter apply(String... args) {
		validate(2, args);
		final String header = args[0];
		final String value = args[1];

		return (exchange, chain) -> {
			exchange.getResponse().getHeaders().set(header, value);

			return chain.filter(exchange);
		};
	}
}
