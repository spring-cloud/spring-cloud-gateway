package org.springframework.cloud.gateway.filter.route;

import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.WebFilter;

import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.isResponseCommitted;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.setResponseStatus;

import reactor.core.publisher.Mono;

/**
 * @author Spencer Gibb
 */
public class SetStatusRouteFilter implements RouteFilter {

	@Override
	public WebFilter apply(String... args) {
		validate(1, args);
		final HttpStatus httpStatus = ServerWebExchangeUtils.parse(args[0]);

		return (exchange, chain) -> {

			// option 1 (runs in filter order)
			/*exchange.getResponse().beforeCommit(() -> {
				exchange.getResponse().setStatusCode(finalStatus);
				return Mono.empty();
			});
			return chain.filter(exchange);*/

			// option 2 (runs in reverse filter order)
			return chain.filter(exchange).then(() -> {
				// check not really needed, since it is guarded in setStatusCode, but it's a good example
				if (!isResponseCommitted(exchange)) {
					setResponseStatus(exchange, httpStatus);
				}
				return Mono.empty();
			});
		};
	}

}
