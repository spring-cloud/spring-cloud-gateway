package org.springframework.cloud.gateway.filter.factory;

import org.springframework.cloud.gateway.filter.GatewayFilter;

/**
 * @author Spencer Gibb
 */
public class AddResponseHeaderFilterFactory implements FilterFactory {

	@Override
	public GatewayFilter apply(String header, String[] args) {
		validate(args, 1);

		//TODO: caching can happen here
		return (exchange, chain) -> {
			exchange.getResponse().getHeaders().add(header, args[0]);

			return chain.filter(exchange);
		};
	}
}
