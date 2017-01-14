package org.springframework.cloud.gateway.filter.definition;

import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.util.Assert;

/**
 * @author Spencer Gibb
 */
public class AppendResponseHeaderFilter implements GatewayFilterDefinition {

	@Override
	public String getName() {
		return "AppendResponseHeader";
	}

	@Override
	public GatewayFilter apply(String header, String[] args) {
		Assert.isTrue(args != null && args.length == 1,
				"args must have one entry");

		//TODO: caching can happen here
		return (exchange, chain) -> {
			exchange.getResponse().getHeaders().add(header, args[0]);

			return chain.filter(exchange);
		};
	}
}
