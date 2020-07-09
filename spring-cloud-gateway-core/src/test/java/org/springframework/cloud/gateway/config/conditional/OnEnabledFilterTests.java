package org.springframework.cloud.gateway.config.conditional;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.cloud.gateway.filter.factory.AddRequestHeaderGatewayFilterFactory;
import org.springframework.cloud.gateway.filter.factory.DedupeResponseHeaderGatewayFilterFactory;
import org.springframework.cloud.gateway.filter.factory.FallbackHeadersGatewayFilterFactory;
import org.springframework.cloud.gateway.filter.factory.GatewayFilterFactory;
import org.springframework.cloud.gateway.filter.factory.HystrixGatewayFilterFactory;
import org.springframework.cloud.gateway.filter.factory.MapRequestHeaderGatewayFilterFactory;
import org.springframework.cloud.gateway.filter.factory.SpringCloudCircuitBreakerResilience4JFilterFactory;

import static org.assertj.core.api.Assertions.assertThat;

class OnEnabledFilterTests {

	private OnEnabledFilter onEnabledFilter;

	@BeforeEach
	void setUp() {
		this.onEnabledFilter = new OnEnabledFilter();
	}

	@Test
	void shouldNormalizePredicatesNames() {
		List<Class<? extends GatewayFilterFactory<?>>> predicates = Arrays.asList(
				AddRequestHeaderGatewayFilterFactory.class,
				DedupeResponseHeaderGatewayFilterFactory.class,
				FallbackHeadersGatewayFilterFactory.class,
				HystrixGatewayFilterFactory.class,
				MapRequestHeaderGatewayFilterFactory.class,
				SpringCloudCircuitBreakerResilience4JFilterFactory.class);

		List<String> resultNames = predicates.stream()
				.map(onEnabledFilter::normalizeComponentName)
				.collect(Collectors.toList());

		List<String> expectedNames = Arrays.asList("add-request-header",
				"dedupe-response-header", "fallback-headers", "hystrix",
				"map-request-header", "circuit-breaker");

		assertThat(resultNames).isEqualTo(expectedNames);
	}

}