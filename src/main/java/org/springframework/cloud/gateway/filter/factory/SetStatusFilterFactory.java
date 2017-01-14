package org.springframework.cloud.gateway.filter.factory;

import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * @author Spencer Gibb
 */
public class SetStatusFilterFactory implements FilterFactory {

	@Override
	public GatewayFilter apply(String statusString, String[] args) {
		HttpStatus httpStatus;

		try {
			int status = Integer.parseInt(statusString);
			httpStatus = HttpStatus.valueOf(status);
		} catch (NumberFormatException e) {
			// try the enum string
			httpStatus = HttpStatus.valueOf(statusString.toUpperCase());
		}

		final HttpStatus finalStatus = httpStatus;


		//TODO: caching can happen here
		return (exchange, chain) ->
			chain.filter(exchange).then(() -> setStatus(exchange, finalStatus));
	}

	protected Mono<Void> setStatus(ServerWebExchange exchange, HttpStatus status) {
		exchange.getResponse().setStatusCode(status);
		return Mono.empty();
	}
}
