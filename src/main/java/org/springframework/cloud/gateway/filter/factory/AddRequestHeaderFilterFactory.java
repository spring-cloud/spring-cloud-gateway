package org.springframework.cloud.gateway.filter.factory;

import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.util.Assert;

/**
 * @author Spencer Gibb
 */
public class AddRequestHeaderFilterFactory implements FilterFactory {

	@Override
	public String getName() {
		return "AddRequestHeader";
	}

	@Override
	public GatewayFilter apply(String header, String[] args) {
		Assert.isTrue(args != null && args.length == 1,
				"args must have one entry");

		//TODO: caching can happen here
		return (exchange, chain) -> {
			ServerHttpRequest request = exchange.getRequest().mutate()
					.header(header, args[0])
					.build();

			return chain.filter(exchange.mutate().request(request).build());
		};
	}
}
