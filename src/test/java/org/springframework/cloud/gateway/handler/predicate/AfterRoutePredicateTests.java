package org.springframework.cloud.gateway.handler.predicate;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

import org.junit.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.http.server.reactive.MockServerHttpResponse;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.adapter.DefaultServerWebExchange;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Spencer Gibb
 */
public class AfterRoutePredicateTests {

	@Test
	public void beforeStringWorks() {
		String dateString = ZonedDateTime.now().minusHours(1).format(DateTimeFormatter.ISO_ZONED_DATE_TIME);

		final boolean result = new AfterRoutePredicate().apply(dateString, null).test(getExchange());

		assertThat(result).isFalse();
	}

	@Test
	public void afterStringWorks() {
		String dateString = ZonedDateTime.now().plusHours(1).format(DateTimeFormatter.ISO_ZONED_DATE_TIME);

		final boolean result = new AfterRoutePredicate().apply(dateString, null).test(getExchange());

		assertThat(result).isTrue();
	}

	@Test
	public void beforeEpochWorks() {
		String dateString = String.valueOf(System.currentTimeMillis()-(1000*60*60));

		final boolean result = new AfterRoutePredicate().apply(dateString, null).test(getExchange());

		assertThat(result).isFalse();
	}

	@Test
	public void afterEpochWorks() {
		String dateString = String.valueOf(System.currentTimeMillis()+(1000*60*60));

		final boolean result = new AfterRoutePredicate().apply(dateString, null).test(getExchange());

		assertThat(result).isTrue();
	}

	private ServerWebExchange getExchange() {
		final MockServerHttpRequest request = MockServerHttpRequest.get("http://example.com").build();
		return new DefaultServerWebExchange(request, new MockServerHttpResponse());
	}
}
