package org.springframework.cloud.gateway.config;

import java.util.List;

import org.junit.Test;
import org.junit.experimental.runners.Enclosed;
import org.junit.runner.RunWith;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.filter.RemoveCachedBodyFilter;
import org.springframework.cloud.gateway.filter.RouteToRequestUrlFilter;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;

import static org.assertj.core.api.Assertions.assertThat;

@RunWith(Enclosed.class)
class EnableDisableBuiltInGlobalFiltersTests {

	@EnableAutoConfiguration
	@SpringBootConfiguration
	protected static class Config {
	}

	@RunWith(SpringRunner.class)
	@SpringBootTest(classes = Config.class)
	public static class GlobalFilterDefault {

		@Autowired
		private List<GlobalFilter> globalFilters;

		@Test
		public void shouldInjectBuiltInFilters() {
			assertThat(globalFilters).hasSizeGreaterThanOrEqualTo(10);
		}

	}

	@RunWith(SpringRunner.class)
	@SpringBootTest(classes = Config.class, properties = {
		"spring.cloud.gateway.remove-cached-body.enabled=false",
		"spring.cloud.gateway.route-to-requestUrl.enabled=false"
	})
	@ActiveProfiles("disable-components")
	public static class DisableSpecificsFiltersByProperty {

		@Autowired
		private List<GlobalFilter> globalFilters;

		@Test
		public void shouldInjectOnlyEnabledBuiltInFilters() {
			assertThat(globalFilters).hasSizeGreaterThan(0);
			assertThat(globalFilters).allSatisfy(filter ->
					assertThat(filter).isNotInstanceOfAny(
							RemoveCachedBodyFilter.class, RouteToRequestUrlFilter.class
					)
			);
		}

	}

	@RunWith(SpringRunner.class)
	@SpringBootTest(classes = Config.class, properties = {
			"spring.cloud.gateway.adapt-cached-body.enabled=false",
			"spring.cloud.gateway.remove-cached-body.enabled=false",
			"spring.cloud.gateway.route-to-request-url.enabled=false",
			"spring.cloud.gateway.forward-routing.enabled=false",
			"spring.cloud.gateway.forward-path.enabled=false",
			"spring.cloud.gateway.websocket-routing.enabled=false",
			"spring.cloud.gateway.weight.enabled=false",
			"spring.cloud.gateway.netty.enabled=false",
			"spring.cloud.gateway.reactive-loadbalancer.enabled=false",
			"spring.cloud.gateway.metrics.enabled=false"
	})
	@ActiveProfiles("disable-components")
	public static class DisableAllGlobalFiltersByProperty {

		@Autowired(required = false)
		private List<GlobalFilter> globalFilters;

		@Test
		public void shouldDisableAllBuiltInFilters() {
			assertThat(globalFilters).isNull();
		}

	}

}