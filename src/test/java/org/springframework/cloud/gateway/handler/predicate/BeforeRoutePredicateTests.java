package org.springframework.cloud.gateway.handler.predicate;

import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.cloud.gateway.handler.predicate.BetweenRoutePredicateTests.getExchange;
import static org.springframework.cloud.gateway.handler.predicate.BetweenRoutePredicateTests.minusHours;
import static org.springframework.cloud.gateway.handler.predicate.BetweenRoutePredicateTests.minusHoursMillis;
import static org.springframework.cloud.gateway.handler.predicate.BetweenRoutePredicateTests.plusHours;
import static org.springframework.cloud.gateway.handler.predicate.BetweenRoutePredicateTests.plusHoursMillis;

/**
 * @author Spencer Gibb
 */
public class BeforeRoutePredicateTests {

	@Test
	public void beforeStringWorks() {
		String dateString = minusHours(1);

		boolean result = runPredicate(dateString);

		assertThat(result).isFalse();
	}

	@Test
	public void afterStringWorks() {
		String dateString = plusHours(1);

		boolean result = runPredicate(dateString);

		assertThat(result).isTrue();
	}

	@Test
	public void beforeEpochWorks() {
		String dateString = minusHoursMillis(1);

		final boolean result = runPredicate(dateString);

		assertThat(result).isFalse();
	}

	@Test
	public void afterEpochWorks() {
		String dateString = plusHoursMillis(1);

		final boolean result = runPredicate(dateString);

		assertThat(result).isTrue();
	}

	private boolean runPredicate(String dateString) {
		return new BeforeRoutePredicate().apply(dateString, null).test(getExchange());
	}
}
