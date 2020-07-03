package org.springframework.cloud.gateway.config;

import java.util.List;

import org.junit.Test;
import org.junit.experimental.runners.Enclosed;
import org.junit.runner.RunWith;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.handler.predicate.AfterRoutePredicateFactory;
import org.springframework.cloud.gateway.handler.predicate.BeforeRoutePredicateFactory;
import org.springframework.cloud.gateway.handler.predicate.RoutePredicateFactory;
import org.springframework.test.context.junit4.SpringRunner;

import static org.assertj.core.api.Assertions.assertThat;

@RunWith(Enclosed.class)
class EnableDisableBuiltInPredicatesTests {

	@EnableAutoConfiguration
	@SpringBootConfiguration
	protected static class Config {
	}

	@RunWith(SpringRunner.class)
	@SpringBootTest(classes = Config.class)
	public static class GlobalFilterDefault {

		@Autowired
		private List<RoutePredicateFactory<?>> predicates;

		@Test
		public void shouldInjectBuiltInFilters() {
			assertThat(predicates).hasSizeGreaterThanOrEqualTo(13);
		}

	}

	@RunWith(SpringRunner.class)
	@SpringBootTest(classes = Config.class, properties = {
		"spring.cloud.gateway.after-route.enabled=false",
		"spring.cloud.gateway.before-route.enabled=false"
	})
	public static class DisableSpecificsFiltersByProperty {

		@Autowired
		private List<RoutePredicateFactory<?>> predicates;

		@Test
		public void shouldInjectOnlyEnabledBuiltInFilters() {
			assertThat(predicates).hasSizeGreaterThan(0);
			assertThat(predicates).allSatisfy(filter ->
					assertThat(filter).isNotInstanceOfAny(
							AfterRoutePredicateFactory.class, BeforeRoutePredicateFactory.class
					)
			);
		}

	}

	@RunWith(SpringRunner.class)
	@SpringBootTest(classes = Config.class, properties = {
			"spring.cloud.gateway.after-route.enabled=false",
			"spring.cloud.gateway.before-route.enabled=false",
			"spring.cloud.gateway.between-route.enabled=false",
			"spring.cloud.gateway.cookie-route.enabled=false",
			"spring.cloud.gateway.header-route.enabled=false",
			"spring.cloud.gateway.host-route.enabled=false",
			"spring.cloud.gateway.method-route.enabled=false",
			"spring.cloud.gateway.path-route.enabled=false",
			"spring.cloud.gateway.query-route.enabled=false",
			"spring.cloud.gateway.read-body.enabled=false",
			"spring.cloud.gateway.remote-addr-route.enabled=false",
			"spring.cloud.gateway.weight-route.enabled=false",
			"spring.cloud.gateway.cloud-foundry-route.enabled=false"
	})
	public static class DisableAllGlobalFiltersByProperty {

		@Autowired(required = false)
		private List<RoutePredicateFactory<?>> predicates;

		@Test
		public void shouldDisableAllBuiltInFilters() {
			assertThat(predicates).isNull();
		}

	}

}