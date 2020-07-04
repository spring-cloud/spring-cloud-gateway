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
			properties = { "spring.cloud.gateway.AddRequestHeader.enabled=false",
					"spring.cloud.gateway.MapRequestHeader.enabled=false" })
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
			properties = { "spring.cloud.gateway.AddRequestHeader.enabled=false",
					"spring.cloud.gateway.MapRequestHeader.enabled=false",
					"spring.cloud.gateway.AddRequestParameter.enabled=false",
					"spring.cloud.gateway.AddResponseHeader.enabled=false",
					"spring.cloud.gateway.ModifyRequestBody.enabled=false",
					"spring.cloud.gateway.DedupeResponseHeader.enabled=false",
					"spring.cloud.gateway.ModifyResponseBody.enabled=false",
					"spring.cloud.gateway.PrefixPath.enabled=false",
					"spring.cloud.gateway.PreserveHostHeader.enabled=false",
					"spring.cloud.gateway.RedirectTo.enabled=false",
					"spring.cloud.gateway.RemoveRequestHeader.enabled=false",
					"spring.cloud.gateway.RemoveRequestParameter.enabled=false",
					"spring.cloud.gateway.RemoveResponseHeader.enabled=false",
					"spring.cloud.gateway.RequestRateLimiter.enabled=false",
					"spring.cloud.gateway.RewritePath.enabled=false",
					"spring.cloud.gateway.Retry.enabled=false",
					"spring.cloud.gateway.SetPath.enabled=false",
					"spring.cloud.gateway.SecureHeaders.enabled=false",
					"spring.cloud.gateway.SetRequestHeader.enabled=false",
					"spring.cloud.gateway.SetRequestHostHeader.enabled=false",
					"spring.cloud.gateway.SetResponseHeader.enabled=false",
					"spring.cloud.gateway.RewriteResponseHeader.enabled=false",
					"spring.cloud.gateway.RewriteLocationResponseHeader.enabled=false",
					"spring.cloud.gateway.RewriteLocation.enabled=false",
					"spring.cloud.gateway.SetStatus.enabled=false",
					"spring.cloud.gateway.SaveSession.enabled=false",
					"spring.cloud.gateway.StripPrefix.enabled=false",
					"spring.cloud.gateway.RequestHeaderToRequestUri.enabled=false",
					"spring.cloud.gateway.RequestSize.enabled=false",
					"spring.cloud.gateway.RequestHeaderSize.enabled=false",
					"spring.cloud.gateway.Fallback.enabled=false",
					"spring.cloud.gateway.FallbackHeaders.enabled=false" })
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
