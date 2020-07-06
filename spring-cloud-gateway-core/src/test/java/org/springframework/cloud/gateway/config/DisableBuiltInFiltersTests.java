/*
 * Copyright 2013-2019 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.cloud.gateway.config;

import java.util.List;

import org.junit.Test;
import org.junit.experimental.runners.Enclosed;
import org.junit.runner.RunWith;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.filter.factory.AddRequestHeaderGatewayFilterFactory;
import org.springframework.cloud.gateway.filter.factory.GatewayFilterFactory;
import org.springframework.cloud.gateway.filter.factory.MapRequestHeaderGatewayFilterFactory;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;

import static org.assertj.core.api.Assertions.assertThat;

@RunWith(Enclosed.class)
public class DisableBuiltInFiltersTests {

	@EnableAutoConfiguration
	@SpringBootConfiguration
	protected static class Config {

	}

	@RunWith(SpringRunner.class)
	@SpringBootTest(classes = Config.class)
	public static class FilterDefault {

		@Autowired
		private List<GatewayFilterFactory<?>> gatewayFilters;

		@Test
		public void shouldInjectBuiltInFilters() {
			assertThat(gatewayFilters).hasSizeGreaterThanOrEqualTo(31);
		}

	}

	@RunWith(SpringRunner.class)
	@SpringBootTest(classes = Config.class,
			properties = { "spring.cloud.gateway.add-request-header.enabled=false",
					"spring.cloud.gateway.map-request-header.enabled=false" })
	@ActiveProfiles("disable-components")
	public static class DisableSpecificsFiltersByProperty {

		@Autowired
		private List<GatewayFilterFactory<?>> gatewayFilters;

		@Test
		public void shouldInjectOnlyEnabledBuiltInFilters() {
			assertThat(gatewayFilters).hasSizeGreaterThan(0);
			assertThat(gatewayFilters).allSatisfy(filter -> assertThat(filter)
					.isNotInstanceOfAny(AddRequestHeaderGatewayFilterFactory.class,
							MapRequestHeaderGatewayFilterFactory.class));
		}

	}

	@RunWith(SpringRunner.class)
	@SpringBootTest(classes = Config.class,
			properties = { "spring.cloud.gateway.add-request-header.enabled=false",
					"spring.cloud.gateway.map-request-header.enabled=false",
					"spring.cloud.gateway.add-request-parameter.enabled=false",
					"spring.cloud.gateway.add-response-header.enabled=false",
					"spring.cloud.gateway.modify-request-body.enabled=false",
					"spring.cloud.gateway.dedupe-response-header.enabled=false",
					"spring.cloud.gateway.modify-response-body.enabled=false",
					"spring.cloud.gateway.prefix-path.enabled=false",
					"spring.cloud.gateway.preserve-host-header.enabled=false",
					"spring.cloud.gateway.redirect-to.enabled=false",
					"spring.cloud.gateway.remove-request-header.enabled=false",
					"spring.cloud.gateway.remove-request-parameter.enabled=false",
					"spring.cloud.gateway.remove-response-header.enabled=false",
					"spring.cloud.gateway.request-rate-limiter.enabled=false",
					"spring.cloud.gateway.rewrite-path.enabled=false",
					"spring.cloud.gateway.retry.enabled=false",
					"spring.cloud.gateway.set-path.enabled=false",
					"spring.cloud.gateway.secure-headers.enabled=false",
					"spring.cloud.gateway.set-request-header.enabled=false",
					"spring.cloud.gateway.SetRequestHostHeader.enabled=false",
					"spring.cloud.gateway.set-response-header.enabled=false",
					"spring.cloud.gateway.rewrite-response-header.enabled=false",
					"spring.cloud.gateway.RewriteLocationResponseHeader.enabled=false",
					"spring.cloud.gateway.rewrite-location.enabled=false",
					"spring.cloud.gateway.set-status.enabled=false",
					"spring.cloud.gateway.save-session.enabled=false",
					"spring.cloud.gateway.strip-prefix.enabled=false",
					"spring.cloud.gateway.RequestHeaderToRequestUri.enabled=false",
					"spring.cloud.gateway.request-size.enabled=false",
					"spring.cloud.gateway.request-header-size.enabled=false",
					"spring.cloud.gateway.fallback.enabled=false",
					"spring.cloud.gateway.fallback-headers.enabled=false" })
	@ActiveProfiles("disable-components")
	public static class DisableAllFiltersByProperty {

		@Autowired(required = false)
		private List<GatewayFilterFactory<?>> gatewayFilters;

		@Test
		public void shouldDisableAllBuiltInFilters() {
			assertThat(gatewayFilters).isNull();
		}

	}

}
