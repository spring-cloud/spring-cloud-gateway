package org.springframework.cloud.gateway.filter.route;

import org.springframework.web.server.WebFilter;

/**
 * @author Spencer Gibb
 */
public class RemoveResponseHeaderRouteFilter implements RouteFilter {

	@Override
	public WebFilter apply(String header, String[] args) {

		//TODO: caching can happen here
		return (exchange, chain) -> {
			exchange.getResponse().getHeaders().remove(header);

			return chain.filter(exchange);
		};
	}
}
