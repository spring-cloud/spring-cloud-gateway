package org.springframework.cloud.gateway.filter.route;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.WebFilter;

import reactor.core.publisher.Mono;

/**
 * @author Spencer Gibb
 */
public class SetStatusRouteFilter implements RouteFilter {

	@Override
	public WebFilter apply(String statusString, String[] args) {
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
			chain.filter(exchange).then(() -> {
				exchange.getResponse().setStatusCode(finalStatus);
				return Mono.empty();
			});
	}
}
