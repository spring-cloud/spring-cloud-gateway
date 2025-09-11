/*
 * Copyright 2013-present the original author or authors.
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
			properties = { "spring.cloud.gateway.server.webflux.filter.add-request-header.enabled=false",
					"spring.cloud.gateway.server.webflux.filter.map-request-header.enabled=false" })
	@ActiveProfiles("disable-components")
	public class DisableSpecificsFiltersByProperty {

		@Autowired
		private List<GatewayFilterFactory<?>> gatewayFilters;

		@Test
		public void shouldInjectOnlyEnabledBuiltInFilters() {
			assertThat(gatewayFilters).hasSizeGreaterThan(0);
			assertThat(gatewayFilters)
				.allSatisfy(filter -> assertThat(filter).isNotInstanceOfAny(AddRequestHeaderGatewayFilterFactory.class,
						MapRequestHeaderGatewayFilterFactory.class));
		}

	}

	@Nested
	@SpringBootTest(classes = Config.class,
			properties = { "spring.cloud.gateway.server.webflux.filter.add-request-header.enabled=false",
					"spring.cloud.gateway.server.webflux.filter.map-request-header.enabled=false",
					"spring.cloud.gateway.server.webflux.filter.add-request-headers-if-not-present.enabled=false",
					"spring.cloud.gateway.server.webflux.filter.add-request-parameter.enabled=false",
					"spring.cloud.gateway.server.webflux.filter.add-response-header.enabled=false",
					"spring.cloud.gateway.server.webflux.filter.json-to-grpc.enabled=false",
					"spring.cloud.gateway.server.webflux.filter.modify-request-body.enabled=false",
					"spring.cloud.gateway.server.webflux.filter.local-response-cache.enabled=false",
					"spring.cloud.gateway.server.webflux.filter.dedupe-response-header.enabled=false",
					"spring.cloud.gateway.server.webflux.filter.modify-response-body.enabled=false",
					"spring.cloud.gateway.server.webflux.filter.prefix-path.enabled=false",
					"spring.cloud.gateway.server.webflux.filter.preserve-host-header.enabled=false",
					"spring.cloud.gateway.server.webflux.filter.redirect-to.enabled=false",
					"spring.cloud.gateway.server.webflux.filter.remove-json-attributes-response-body.enabled=false",
					"spring.cloud.gateway.server.webflux.filter.remove-request-header.enabled=false",
					"spring.cloud.gateway.server.webflux.filter.remove-request-parameter.enabled=false",
					"spring.cloud.gateway.server.webflux.filter.remove-response-header.enabled=false",
					"spring.cloud.gateway.server.webflux.filter.request-rate-limiter.enabled=false",
					"spring.cloud.gateway.server.webflux.filter.rewrite-path.enabled=false",
					"spring.cloud.gateway.server.webflux.filter.retry.enabled=false",
					"spring.cloud.gateway.server.webflux.filter.set-path.enabled=false",
					"spring.cloud.gateway.server.webflux.filter.secure-headers.enabled=false",
					"spring.cloud.gateway.server.webflux.filter.set-request-header.enabled=false",
					"spring.cloud.gateway.server.webflux.filter.set-request-host-header.enabled=false",
					"spring.cloud.gateway.server.webflux.filter.set-request-uri.enabled=false",
					"spring.cloud.gateway.server.webflux.filter.set-response-header.enabled=false",
					"spring.cloud.gateway.server.webflux.filter.rewrite-response-header.enabled=false",
					"spring.cloud.gateway.server.webflux.filter.rewrite-location-response-header.enabled=false",
					"spring.cloud.gateway.server.webflux.filter.rewrite-location.enabled=false",
					"spring.cloud.gateway.server.webflux.filter.rewrite-request-parameter.enabled=false",
					"spring.cloud.gateway.server.webflux.filter.set-status.enabled=false",
					"spring.cloud.gateway.server.webflux.filter.save-session.enabled=false",
					"spring.cloud.gateway.server.webflux.filter.strip-prefix.enabled=false",
					"spring.cloud.gateway.server.webflux.filter.request-header-to-request-uri.enabled=false",
					"spring.cloud.gateway.server.webflux.filter.request-size.enabled=false",
					"spring.cloud.gateway.server.webflux.filter.request-header-size.enabled=false",
					"spring.cloud.gateway.server.webflux.filter.circuit-breaker.enabled=false",
					"spring.cloud.gateway.server.webflux.filter.token-relay.enabled=false",
					"spring.cloud.gateway.server.webflux.filter.cache-request-body.enabled=false",
					"spring.cloud.gateway.server.webflux.filter.fallback-headers.enabled=false" })
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
