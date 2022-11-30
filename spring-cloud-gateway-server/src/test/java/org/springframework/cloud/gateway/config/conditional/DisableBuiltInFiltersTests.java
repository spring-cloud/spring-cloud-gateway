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

package org.springframework.cloud.gateway.config.conditional;

import java.util.List;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.filter.factory.AddRequestHeaderGatewayFilterFactory;
import org.springframework.cloud.gateway.filter.factory.GatewayFilterFactory;
import org.springframework.cloud.gateway.filter.factory.MapRequestHeaderGatewayFilterFactory;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

public class DisableBuiltInFiltersTests {

	@EnableAutoConfiguration
	@SpringBootConfiguration
	protected static class Config {

	}

	@Nested
	@SpringBootTest(classes = Config.class)
	public class FilterDefault {

		@Autowired
		private List<GatewayFilterFactory<?>> gatewayFilters;

		@Test
		public void shouldInjectBuiltInFilters() {
			assertThat(gatewayFilters).hasSizeGreaterThanOrEqualTo(31);
		}

	}

	@Nested
	@SpringBootTest(classes = Config.class,
			properties = { "spring.cloud.gateway.filter.add-request-header.enabled=false",
					"spring.cloud.gateway.filter.map-request-header.enabled=false" })
	@ActiveProfiles("disable-components")
	public class DisableSpecificsFiltersByProperty {

		@Autowired
		private List<GatewayFilterFactory<?>> gatewayFilters;

		@Test
		public void shouldInjectOnlyEnabledBuiltInFilters() {
			assertThat(gatewayFilters).hasSizeGreaterThan(0);
			assertThat(gatewayFilters).allSatisfy(filter -> assertThat(filter).isNotInstanceOfAny(
					AddRequestHeaderGatewayFilterFactory.class, MapRequestHeaderGatewayFilterFactory.class));
		}

	}

	@Nested
	@SpringBootTest(classes = Config.class,
			properties = { "spring.cloud.gateway.filter.add-request-header.enabled=false",
					"spring.cloud.gateway.filter.map-request-header.enabled=false",
					"spring.cloud.gateway.filter.add-request-headers-if-not-present.enabled=false",
					"spring.cloud.gateway.filter.add-request-parameter.enabled=false",
					"spring.cloud.gateway.filter.add-response-header.enabled=false",
					"spring.cloud.gateway.filter.json-to-grpc.enabled=false",
					"spring.cloud.gateway.filter.modify-request-body.enabled=false",
					"spring.cloud.gateway.filter.local-response-cache.enabled=false",
					"spring.cloud.gateway.filter.dedupe-response-header.enabled=false",
					"spring.cloud.gateway.filter.modify-response-body.enabled=false",
					"spring.cloud.gateway.filter.prefix-path.enabled=false",
					"spring.cloud.gateway.filter.preserve-host-header.enabled=false",
					"spring.cloud.gateway.filter.redirect-to.enabled=false",
					"spring.cloud.gateway.filter.remove-json-attributes-response-body.enabled=false",
					"spring.cloud.gateway.filter.remove-request-header.enabled=false",
					"spring.cloud.gateway.filter.remove-request-parameter.enabled=false",
					"spring.cloud.gateway.filter.remove-response-header.enabled=false",
					"spring.cloud.gateway.filter.request-rate-limiter.enabled=false",
					"spring.cloud.gateway.filter.rewrite-path.enabled=false",
					"spring.cloud.gateway.filter.retry.enabled=false",
					"spring.cloud.gateway.filter.set-path.enabled=false",
					"spring.cloud.gateway.filter.secure-headers.enabled=false",
					"spring.cloud.gateway.filter.set-request-header.enabled=false",
					"spring.cloud.gateway.filter.set-request-host-header.enabled=false",
					"spring.cloud.gateway.filter.set-response-header.enabled=false",
					"spring.cloud.gateway.filter.rewrite-response-header.enabled=false",
					"spring.cloud.gateway.filter.rewrite-location-response-header.enabled=false",
					"spring.cloud.gateway.filter.rewrite-location.enabled=false",
					"spring.cloud.gateway.filter.set-status.enabled=false",
					"spring.cloud.gateway.filter.save-session.enabled=false",
					"spring.cloud.gateway.filter.strip-prefix.enabled=false",
					"spring.cloud.gateway.filter.request-header-to-request-uri.enabled=false",
					"spring.cloud.gateway.filter.request-size.enabled=false",
					"spring.cloud.gateway.filter.request-header-size.enabled=false",
					"spring.cloud.gateway.filter.circuit-breaker.enabled=false",
					"spring.cloud.gateway.filter.token-relay.enabled=false",
					"spring.cloud.gateway.filter.cache-request-body.enabled=false",
					"spring.cloud.gateway.filter.fallback-headers.enabled=false" })
	@ActiveProfiles("disable-components")
	public class DisableAllFiltersByProperty {

		@Autowired(required = false)
		private List<GatewayFilterFactory<?>> gatewayFilters;

		@Test
		public void shouldDisableAllBuiltInFilters() {
			assertThat(gatewayFilters).isNull();
		}

	}

}
