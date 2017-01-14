package org.springframework.cloud.gateway.filter.route;

import org.springframework.web.server.WebFilter;
import org.springframework.http.server.reactive.ServerHttpRequest;

/**
 * @author Spencer Gibb
 */
public class AddRequestHeaderRouteFilter implements RouteFilter {

	@Override
	public WebFilter apply(String header, String[] args) {
		validate(args, 1);

		//TODO: caching can happen here
		return (exchange, chain) -> {
			ServerHttpRequest request = exchange.getRequest().mutate()
					.header(header, args[0])
					.build();

			return chain.filter(exchange.mutate().request(request).build());
		};
	}
}
