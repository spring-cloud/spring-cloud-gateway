package org.springframework.cloud.gateway.config.conditional;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.cloud.gateway.handler.predicate.AfterRoutePredicateFactory;
import org.springframework.cloud.gateway.handler.predicate.CloudFoundryRouteServiceRoutePredicateFactory;
import org.springframework.cloud.gateway.handler.predicate.ReadBodyPredicateFactory;
import org.springframework.cloud.gateway.handler.predicate.RemoteAddrRoutePredicateFactory;
import org.springframework.cloud.gateway.handler.predicate.RoutePredicateFactory;

import static org.assertj.core.api.Assertions.assertThat;

class OnEnabledPredicateTests {

	private OnEnabledPredicate onEnabledPredicate;

	@BeforeEach
	void setUp() {
		this.onEnabledPredicate = new OnEnabledPredicate();
	}

	@Test
	void shouldNormalizePredicatesNames() {
		List<Class<? extends RoutePredicateFactory<?>>> predicates = Arrays.asList(
				AfterRoutePredicateFactory.class,
				CloudFoundryRouteServiceRoutePredicateFactory.class,
				ReadBodyPredicateFactory.class,
				RemoteAddrRoutePredicateFactory.class
		);

		List<String> resultNames = predicates.stream()
				.map(onEnabledPredicate::normalizeComponentName)
				.collect(Collectors.toList());

		List<String> expectedNames = Arrays.asList("after",
				"cloud-foundry-route-service",
				"read-body-predicate-factory",
				"remote-addr");

		assertThat(resultNames).isEqualTo(expectedNames);
	}
}