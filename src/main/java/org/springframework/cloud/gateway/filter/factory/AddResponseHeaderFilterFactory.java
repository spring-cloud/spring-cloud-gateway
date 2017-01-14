package org.springframework.cloud.gateway.filter.factory;

import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.util.Assert;

/**
 * @author Spencer Gibb
 */
public class AddResponseHeaderFilterFactory implements FilterFactory {

	@Override
	public String getName() {
		return "AddResponseHeader";
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
