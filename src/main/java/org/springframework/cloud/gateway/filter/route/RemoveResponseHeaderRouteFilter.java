package org.springframework.cloud.gateway.filter.route;

import org.springframework.web.server.WebFilter;

/**
 * @author Spencer Gibb
 */
public class RemoveResponseHeaderRouteFilter implements RouteFilter {

	@Override
	public WebFilter apply(String... args) {
		validate(1, args);
		final String header = args[0];

		return (exchange, chain) -> {
			exchange.getResponse().getHeaders().remove(header);

			return chain.filter(exchange);
		};
	}
}
