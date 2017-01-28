package org.springframework.cloud.gateway.filter.route;

import org.springframework.web.server.WebFilter;
import org.springframework.http.server.reactive.ServerHttpRequest;

/**
 * @author Spencer Gibb
 */
public class RewritePathRouteFilter implements RouteFilter {

	@Override
	public WebFilter apply(String... args) {
		validate(2, args);
		final String regex = args[0];
		String replacement = args[1].replace("$\\", "$");

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
