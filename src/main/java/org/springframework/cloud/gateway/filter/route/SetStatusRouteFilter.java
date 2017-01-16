package org.springframework.cloud.gateway.filter.route;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.WebFilter;

import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.isResponseCommitted;

import reactor.core.publisher.Mono;

/**
 * @author Spencer Gibb
 */
public class SetStatusRouteFilter implements RouteFilter {
	protected final Log logger = LogFactory.getLog(getClass());

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
					boolean response = exchange.getResponse().setStatusCode(finalStatus);
					if (!response && logger.isWarnEnabled()) {
						logger.warn("Unable to set status code to "+ finalStatus + ". Response already committed.");
					}
				}
				return Mono.empty();
			});
		};
	}

}
