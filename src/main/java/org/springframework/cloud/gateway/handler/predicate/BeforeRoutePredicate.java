package org.springframework.cloud.gateway.handler.predicate;

import java.time.ZonedDateTime;
import java.util.function.Predicate;

import org.springframework.web.server.ServerWebExchange;

/**
 * @author Spencer Gibb
 */
public class BeforeRoutePredicate implements RoutePredicate {

	@Override
	public Predicate<ServerWebExchange> apply(String dateString, String[] args) {
		//TODO: is ZonedDateTime the right thing to use?
		try {
			final long epoch = Long.parseLong(dateString);

			return exchange -> {
				final long now = System.currentTimeMillis();
				return epoch < now;
			};
		} catch (NumberFormatException e) {
			// try ZonedDateTime instead
			final ZonedDateTime dateTime = ZonedDateTime.parse(dateString);

			return exchange -> {
				final ZonedDateTime now = ZonedDateTime.now();
				return dateTime.isBefore(now);
			};
		}
	}
}
