package org.springframework.cloud.gateway.handler.predicate;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.function.Predicate;

import org.springframework.util.Assert;
import org.springframework.web.server.ServerWebExchange;

/**
 * @author Spencer Gibb
 */
public class BetweenRoutePredicate implements RoutePredicate {

	@Override
	public Predicate<ServerWebExchange> apply(String dateString, String[] args) {
		validate(args, 1);

		//TODO: is ZonedDateTime the right thing to use?
		final ZonedDateTime dateTime1 = parseZonedDateTime(dateString);
		final ZonedDateTime dateTime2 = parseZonedDateTime(args[0]);
		Assert.isTrue(dateTime1.isBefore(dateTime2));

		return exchange -> {
			final ZonedDateTime now = ZonedDateTime.now();
			return now.isAfter(dateTime1) && now.isBefore(dateTime2);
		};
	}

	public static ZonedDateTime parseZonedDateTime(String dateString) {
		ZonedDateTime dateTime;
		try {
			long epoch = Long.parseLong(dateString);

			dateTime = Instant.ofEpochMilli(epoch).atOffset(ZoneOffset.ofTotalSeconds(0))
					.toZonedDateTime();
		} catch (NumberFormatException e) {
			// try ZonedDateTime instead
			dateTime = ZonedDateTime.parse(dateString);
		}

		return dateTime;
	}

}
