package org.springframework.cloud.gateway.filter;

import java.util.List;

import org.junit.Test;
import org.junit.experimental.runners.Enclosed;
import org.junit.runner.RunWith;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import static org.assertj.core.api.Assertions.assertThat;

@RunWith(Enclosed.class)
class EnableDisableBuiltInFiltersTests {

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
			assertThat(globalFilters).hasSize(10);
		}

	}

	@RunWith(SpringRunner.class)
	@SpringBootTest(classes = Config.class, properties = {
		"spring.cloud.gateway.built-in.filters.RemoveCachedBody.enabled=false",
		"spring.cloud.gateway.built-in.filters.RouteToRequestUrl.enabled=false"
	})
	public static class DisableSpecificsFiltersByProperty {

		@Autowired
		private List<GlobalFilter> globalFilters;

		@Test
		public void shouldInjectBuiltInFilters() {
			assertThat(globalFilters).allSatisfy(filter ->
					assertThat(filter).isNotInstanceOfAny(
							RemoveCachedBodyFilter.class, RouteToRequestUrlFilter.class
					)
			);
		}

	}

}