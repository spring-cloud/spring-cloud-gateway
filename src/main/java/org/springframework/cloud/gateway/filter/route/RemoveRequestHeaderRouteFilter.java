package org.springframework.cloud.gateway.filter.route;

import org.springframework.web.server.WebFilter;
import org.springframework.http.server.reactive.ServerHttpRequest;

/**
 * @author Spencer Gibb
 */
public class RemoveRequestHeaderRouteFilter implements RouteFilter {

	public static final String FAKE_HEADER = "_______force_______";

	@Override
	public WebFilter apply(String header, String[] args) {

		//TODO: caching can happen here
		return (exchange, chain) -> {
			ServerHttpRequest request = exchange.getRequest().mutate()
					.header(FAKE_HEADER, "mutable") //TODO: is there a better way?
					.build();

			request.getHeaders().remove(FAKE_HEADER);
			request.getHeaders().remove(header);

			return chain.filter(exchange.mutate().request(request).build());
		};
	}
}
