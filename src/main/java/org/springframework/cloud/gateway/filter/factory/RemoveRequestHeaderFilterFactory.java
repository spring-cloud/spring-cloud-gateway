package org.springframework.cloud.gateway.filter.factory;

import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.http.server.reactive.ServerHttpRequest;

/**
 * @author Spencer Gibb
 */
public class RemoveRequestHeaderFilterFactory implements FilterFactory {

	public static final String FAKE_HEADER = "_______force_______";

	@Override
	public GatewayFilter apply(String header, String[] args) {

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
