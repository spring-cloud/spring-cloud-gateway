package org.springframework.cloud.gateway.filter.factory;

import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.http.server.reactive.ServerHttpRequest;

/**
 * @author Spencer Gibb
 */
public class RewritePathFilterFactory implements FilterFactory {

	@Override
	public GatewayFilter apply(String regex, String[] args) {
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
