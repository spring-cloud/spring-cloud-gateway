package org.springframework.cloud.gateway.handler.predicate;

import java.time.ZonedDateTime;
import java.util.function.Predicate;

import org.springframework.web.server.ServerWebExchange;

import static org.springframework.cloud.gateway.handler.predicate.BetweenRoutePredicate.parseZonedDateTime;

/**
 * @author Spencer Gibb
 */
public class BeforeRoutePredicate implements RoutePredicate {

	@Override
	public Predicate<ServerWebExchange> apply(String dateString, String[] args) {
		final ZonedDateTime dateTime = parseZonedDateTime(dateString);

		return exchange -> {
			final ZonedDateTime now = ZonedDateTime.now();
			return now.isBefore(dateTime);
		};
	}

}
