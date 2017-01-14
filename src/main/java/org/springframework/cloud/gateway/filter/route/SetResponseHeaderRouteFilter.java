package org.springframework.cloud.gateway.filter.route;

import org.springframework.web.server.WebFilter;

/**
 * @author Spencer Gibb
 */
public class SetResponseHeaderRouteFilter implements RouteFilter {

	@Override
	public WebFilter apply(String header, String[] args) {
		validate(args, 1);

		//TODO: caching can happen here
		return (exchange, chain) -> {
			exchange.getResponse().getHeaders().set(header, args[0]);

			return chain.filter(exchange);
		};
	}
}
