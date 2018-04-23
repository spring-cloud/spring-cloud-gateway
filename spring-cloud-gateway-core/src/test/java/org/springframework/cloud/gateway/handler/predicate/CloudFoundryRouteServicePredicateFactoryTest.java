package org.springframework.cloud.gateway.handler.predicate;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.function.Predicate;

import org.junit.Before;
import org.junit.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;

/**
 * @author Andrew Fitzgerald
 */
public class CloudFoundryRouteServicePredicateFactoryTest {

	private Predicate<ServerWebExchange> predicate;

	@Before
	public void setUp() throws Exception {
		CloudFoundryRouteServicePredicateFactory factory = new CloudFoundryRouteServicePredicateFactory();
		predicate = factory.apply(factory.newConfig());
	}

	@Test
	public void itReturnsTrueWithAllHeadersPresent() {
		MockServerHttpRequest request = MockServerHttpRequest.get("someurl")
				.header(CloudFoundryRouteServicePredicateFactory.X_CF_FORWARDED_URL,
						"url")
				.header(CloudFoundryRouteServicePredicateFactory.X_CF_PROXY_METADATA,
						"metadata")
				.header(CloudFoundryRouteServicePredicateFactory.X_CF_PROXY_SIGNATURE,
						"signature")
				.build();
		MockServerWebExchange exchange = MockServerWebExchange.from(request);

		assertThat(predicate.test(exchange)).isTrue();
	}

	@Test
	public void itReturnsFalseWithAHeadersMissing() {
		MockServerHttpRequest request = MockServerHttpRequest.get("someurl")
				.header(CloudFoundryRouteServicePredicateFactory.X_CF_FORWARDED_URL,
						"url")
				.header(CloudFoundryRouteServicePredicateFactory.X_CF_PROXY_METADATA,
						"metadata")
				.build();
		MockServerWebExchange exchange = MockServerWebExchange.from(request);

		assertThat(predicate.test(exchange)).isFalse();
	}

	@Test
	public void itReturnsFalseWithNoHeaders() {
		MockServerHttpRequest request = MockServerHttpRequest.get("someurl").build();
		MockServerWebExchange exchange = MockServerWebExchange.from(request);

		assertThat(predicate.test(exchange)).isFalse();
	}
}