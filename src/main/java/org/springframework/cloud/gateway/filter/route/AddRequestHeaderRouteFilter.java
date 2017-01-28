package org.springframework.cloud.gateway.filter.route;

import org.springframework.web.server.WebFilter;
import org.springframework.http.server.reactive.ServerHttpRequest;

/**
 * @author Spencer Gibb
 */
public class AddRequestHeaderRouteFilter implements RouteFilter {

	@Override
	public WebFilter apply(String... args) {
		validate(2, args);

		return (exchange, chain) -> {
			ServerHttpRequest request = exchange.getRequest().mutate()
					.header(args[0], args[1])
					.build();

			return chain.filter(exchange.mutate().request(request).build());
		};
	}
}
