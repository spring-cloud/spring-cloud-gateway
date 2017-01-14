package org.springframework.cloud.gateway.filter.route;

import org.springframework.web.server.WebFilter;
import org.springframework.http.server.reactive.ServerHttpRequest;

/**
 * @author Spencer Gibb
 */
public class RewritePathRouteFilter implements RouteFilter {

	@Override
	public WebFilter apply(String regex, String[] args) {
		validate(args, 1);
		String replacement = args[0].replace("$\\", "$");

		//TODO: caching can happen here
		return (exchange, chain) -> {
			ServerHttpRequest req = exchange.getRequest();
			String path = req.getURI().getPath();
			String newPath = path.replaceAll(regex, replacement);

			ServerHttpRequest request = req.mutate()
					.path(newPath)
					.build();

			return chain.filter(exchange.mutate().request(request).build());
		};
	}
}
