package org.springframework.cloud.gateway.filter.factory;

import org.springframework.cloud.gateway.filter.GatewayFilter;

/**
 * @author Spencer Gibb
 */
public class RemoveResponseHeaderFilterFactory implements FilterFactory {

	@Override
	public GatewayFilter apply(String header, String[] args) {

		//TODO: caching can happen here
		return (exchange, chain) -> {
			exchange.getResponse().getHeaders().remove(header);

			return chain.filter(exchange);
		};
	}
}
